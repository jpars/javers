package org.javers.core.json.typeadapter

import com.google.common.collect.HashMultiset
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.javers.core.GlobalIdTestBuilder
import org.javers.core.commit.CommitId
import org.javers.core.commit.CommitMetadata
import org.javers.core.json.typeadapter.util.UtilTypeCoreAdapters
import org.javers.core.metamodel.object.CdoSnapshot
import org.javers.core.model.DummyUser
import org.javers.core.model.DummyUserDetails
import org.javers.core.model.SnapshotEntity
import org.javers.guava.MultimapBuilder
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.ZonedDateTime

import static org.javers.core.GlobalIdTestBuilder.instanceId
import static org.javers.core.JaversTestBuilder.javersTestAssembly
import static org.javers.core.model.DummyUser.dummyUser

/**
 * @author pawel szymczyk
 */
class CdoSnapshotTypeAdapterTest extends Specification {

    def "should serialize CdoSnapshot to Json"() {
        given:
        def now = ZonedDateTime.now()
        def javers = javersTestAssembly()

        def dummyUserCdo = javers.createCdoWrapper( new DummyUser(name:"kaz", age:5) )
        def snapshot = javers.snapshotFactory.createInitial(dummyUserCdo, someCommitMetadata(now))

        when:
        def jsonText = javers.jsonConverter.toJson(snapshot)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.commitMetadata.id == 1.00
        json.commitMetadata.author == "kazik"
        json.commitMetadata.commitDate == UtilTypeCoreAdapters.serialize(now.toLocalDateTime())
        json.commitMetadata.commitDateInstant == UtilTypeCoreAdapters.serialize(now.toInstant())
        json.changedProperties == ["name","age"]

        json.globalId.entity == "org.javers.core.model.DummyUser"
        json.globalId.cdoId == "kaz"
        json.type == "INITIAL"
        json.version == 1
    }

    def "should serialize state with primitive values in CdoSnapshot"() {
        given:
        def javers = javersTestAssembly()
        def dummyUser = new DummyUser(
                name:'kaz',
                age:1,
                flag:true,
                _char:'a')

        def dummyUserCdo = javers.createCdoWrapper( dummyUser )
        def snapshot = javers.snapshotFactory.createInitial(dummyUserCdo, someCommitMetadata())

        when:
        String jsonText = javers.jsonConverter.toJson(snapshot)

        then:
        def json = new JsonSlurper().parseText(jsonText)

        with(json.state) {
            name == "kaz"
            age == 1
            flag == true
            _char == 'a' as char
        }
    }

    def "should serialize state with entity in CdoSnapshot"() {
        given:
        def javers = javersTestAssembly()
        def dummyUser = dummyUser("kaz").withDetails()
        def cdoWrapper = javers.createCdoWrapper( dummyUser )
        def snapshot = javers.snapshotFactory.createInitial(cdoWrapper, someCommitMetadata())

        when:
        String jsonText = javers.jsonConverter.toJson(snapshot)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.state.dummyUserDetails.entity == "org.javers.core.model.DummyUserDetails"
    }

    def "should serialize state with value object in CdoSnapshot"() {
        given:
        def javers = javersTestAssembly()
        def dummyUserDetails = DummyUserDetails.dummyUserDetails(1).withAddress("London", "St John Street")

        def cdoWrapper = javers.createCdoWrapper( dummyUserDetails )
        def snapshot = javers.snapshotFactory.createInitial(cdoWrapper, someCommitMetadata())

        when:
        String jsonText = javers.jsonConverter.toJson(snapshot)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.state.dummyAddress.fragment == "dummyAddress"
    }

    def "should serialize & deserialize state with Map of Entities"(){
      given:
      def javers = javersTestAssembly()
      def entity = new SnapshotEntity(id:1, mapOfEntities: [(new SnapshotEntity(id:2)) : new SnapshotEntity(id:3)])

      def cdoWrapper = javers.createCdoWrapper( entity )
      def snapshot = javers.snapshotFactory.createInitial(cdoWrapper, someCommitMetadata())

      when:
      def jsonText = javers.jsonConverter.toJson(snapshot)
      def deserializedSnapshot = javersTestAssembly().jsonConverter.fromJson(jsonText, CdoSnapshot)

      then:
      snapshot.getPropertyValue("mapOfEntities") == deserializedSnapshot.getPropertyValue("mapOfEntities")
    }

    def "should serialize & deserialize state with Multimaps in CdoSnapshots"() {
        given:
        def javers = javersTestAssembly()
        def entity = new SnapshotEntity(id: 1,
                multiMapPrimitiveToEntity: MultimapBuilder.create(["NY": [new SnapshotEntity(id: 2), new SnapshotEntity(id: 3)]]))

        def cdoWrapper = javers.createCdoWrapper( entity )
        def snapshot = javers.snapshotFactory.createInitial(cdoWrapper, someCommitMetadata())

        when:
        def jsonText = javers.jsonConverter.toJson(snapshot)

        then:
        def jsonMultimap = new JsonSlurper().parseText(jsonText).state.multiMapPrimitiveToEntity
        jsonMultimap == [
                [key:'NY', value: [entity: 'org.javers.core.model.SnapshotEntity', cdoId: 2]],
                [key:'NY', value: [entity: 'org.javers.core.model.SnapshotEntity', cdoId: 3]]
        ]

        when:
        def deserializedSnapshot = javersTestAssembly().jsonConverter.fromJson(jsonText, CdoSnapshot)

        then:
        snapshot.getPropertyValue("mapOfEntities") == deserializedSnapshot.getPropertyValue("mapOfEntities")
    }

    def "should serialize state with Multisets in CdoSnapshots"() {
        given:
        def javers = javersTestAssembly()
        def entity = new SnapshotEntity(multiSetOfPrimitives: HashMultiset.create(['a','a','b']))

        def cdoWrapper = javers.createCdoWrapper( entity )
        def snapshot = javers.snapshotFactory.createInitial(cdoWrapper, someCommitMetadata())

        when:
        def jsonText = javers.jsonConverter.toJson(snapshot)
        println jsonText

        then:
        def jsonMultiset = new JsonSlurper().parseText(jsonText).state.multiSetOfPrimitives
        jsonMultiset == ['a','a','b']
    }

    def "should serialize state with collections in CdoSnapshots"() {
        given:
        def javers = javersTestAssembly()
        def dummyUser = new DummyUser(name: "kaz",
                intArray: [1, 2],
                integerList: [3, 4],
                stringSet: ["5", "6"] as Set,
                primitiveMap: [time: LocalDateTime.of(2000, 1, 1, 12, 0, 0)] )

        def cdoWrapper = javers.createCdoWrapper( dummyUser )
        def snapshot = javers.snapshotFactory.createInitial(cdoWrapper, someCommitMetadata())

        when:
        String jsonText = javers.jsonConverter.toJson(snapshot)

        then:
        def json = new JsonSlurper().parseText jsonText

        with (json.state) {
            intArray == [1, 2]
            integerList == [3, 4]
            stringSet == ["5", "6"]
            primitiveMap.time == "2000-01-01T12:00:00"
        }
    }

    def "should deserialize CdoSnapshot state event if user's class is strongly refactored"(){
      given:
        def json = """
        { 
          "commitMetadata": {
            "id": "1.0",
            "author": "author",
            "commitDate": "2000-01-01T12:00:00"
          },
          "globalId": {
            "entity": "org.javers.core.model.DummyUser",
            "cdoId": "kaz"
          },
          "type": "INITIAL",
          "state": {
            "name": "kaz",
            "stringSet": [1,2,3],
            "integerList": ["1","2","3"],
            "age": "10",
            "someAge": 1.22,
            "someStr": "ab",
            "someList": [
              1,
              2,
              3
            ],
            "someReference": {
              "entity": "org.javers.core.model.DummyUserDetails",
              "cdoId": 1
            }
          },
          "changedProperties": [],
          "version": 1
        }"""

      when:
      def snapshot = javersTestAssembly().jsonConverter.fromJson(json, CdoSnapshot)

      then:
        snapshot.commitMetadata.id.value() == "1.0"
        snapshot.commitMetadata.author == "author"
        snapshot.globalId == instanceId("kaz",DummyUser)
        snapshot.initial == true

        snapshot.state.propertyNames as Set == ["stringSet","integerList", "someStr", "someAge", "name", "someList", "age", "someReference"] as Set
        snapshot.state.getPropertyValue("age") == "10"
        snapshot.state.getPropertyValue("integerList") == ["1","2","3"]
        snapshot.state.getPropertyValue("stringSet") == [1.0, 2.0, 3.0] as Set
        snapshot.state.getPropertyValue("someAge") == 1.22
        snapshot.state.getPropertyValue("someList") == [1.0, 2.0, 3.0]
        snapshot.state.getPropertyValue("someStr") == "ab"
        snapshot.state.getPropertyValue("someReference") == instanceId("1",DummyUserDetails)
    }

    def "should deserialize CdoSnapshot metadata"() {
        given:
        def changed = ["name", "age"]
        def commitProperties = [["key" : "os", "value" : "Solaris"]]
        def json = new JsonBuilder()
        json {
            commitMetadata {
                id "1.0"
                author "author"
                properties commitProperties
                commitDate "2000-01-01T12:00:00.001"
                commitDateInstant "2000-01-01T12:00:00.001Z"
            }
            globalId {
                entity "org.javers.core.model.DummyUser"
                cdoId "kaz"
            }
            type "INITIAL"
            state {
            }
            changedProperties changed
            version 5
        }

        when:
        def snapshot = javersTestAssembly().jsonConverter.fromJson(json.toString(), CdoSnapshot)

        then:
        snapshot.commitMetadata.id.value() == "1.0"
        snapshot.commitMetadata.author == "author"
        snapshot.commitMetadata.properties == ["os" : "Solaris"]
        snapshot.commitMetadata.commitDate.toString() == "2000-01-01T12:00:00.001"
        snapshot.commitMetadata.commitDateInstant.toString() == "2000-01-01T12:00:00.001Z"
        snapshot.globalId == instanceId("kaz",DummyUser)
        snapshot.initial == true
        snapshot.changed.collect{it} as Set == ["name", "age"] as Set
        snapshot.version == 5L
    }

    def "should deserialize CommitMetadata.commitDateInstant guessed from commitDate when missing"(){
        given:
        def json = new JsonBuilder()
        json {
            commitMetadata {
                id "1.0"
                author "author"
                commitDate "2000-01-01T12:00:00.001"
            }
            globalId {
                entity "org.javers.core.model.DummyUser"
                cdoId "kaz"
            }
            state {
            }
        }

        when:
        def snapshot = javersTestAssembly().jsonConverter.fromJson(json.toString(), CdoSnapshot)

        then:
        def expectedInstant = snapshot.commitMetadata.commitDate.toInstant(ZonedDateTime.now().getOffset())
        snapshot.commitMetadata.commitDate.toString() == "2000-01-01T12:00:00.001"
        snapshot.commitMetadata.commitDateInstant == expectedInstant
    }

    def "should deserialize CdoSnapshot.version to 0 when version field is missing"() {

        given:
        def json = new JsonBuilder()
        json {
            commitMetadata {
                id "1.0"
                author "author"
                commitDate "2000-01-01T12:00:00"
            }
            globalId {
                entity "org.javers.core.model.DummyUser"
                cdoId "kaz"
            }
            state {
            }
        }

        when:
        def snapshot = javersTestAssembly().jsonConverter.fromJson(json.toString(), CdoSnapshot)

        then:
        snapshot.version == 0
    }

    def "should deserialize CdoSnapshot state with primitive values"() {

        given:
        def json = new JsonBuilder()
        json {
            commitMetadata {
                id "1.0"
                author "author"
                commitDate "2000-01-01T12:00:00"
            }
            globalId {
                entity "org.javers.core.model.DummyUser"
                cdoId "kaz"
            }
            state {
                _char "a"
                name "kaz"
                age 1
                flag true
            }
        }

        when:
        def snapshot = javersTestAssembly().jsonConverter.fromJson(json.toString(), CdoSnapshot)

        then:
        with (snapshot) {
            getPropertyValue("_char") == 'a' as char
            getPropertyValue("name") == "kaz"
            getPropertyValue("age") == 1
            getPropertyValue("flag") == true
        }
    }

    def "should deserialize CdoSnapshot state with Entity reference"() {

        given:
        def json = new JsonBuilder()
        json {
            commitMetadata {
                id "1.0"
                author "author"
                commitDate "2000-01-01T12:00:00"
            }
            globalId {
                entity "org.javers.core.model.DummyUser"
                cdoId "kaz"
            }
            state {
                name "kaz"
                dummyUserDetails {
                    entity "org.javers.core.model.DummyUserDetails"
                    cdoId 1
                }
            }
        }

        when:
        def snapshot = javersTestAssembly().jsonConverter.fromJson(json.toString(), CdoSnapshot)

        then:
        def entityId = snapshot.getPropertyValue("dummyUserDetails")
        entityId == instanceId(1, DummyUserDetails)
    }

    def "should deserialize CdoSnapshot state with ValueObject reference"() {

        given:
        def json = new JsonBuilder()
        json {
            commitMetadata {
                id "1.0"
                author "author"
                commitDate "2000-01-01T12:00:00"
            }
            globalId {
                entity "org.javers.core.model.DummyUserDetails"
                cdoId 1
            }
            state {
                dummyAddress {
                    valueObject "org.javers.core.model.DummyAddress"
                    ownerId {
                        entity "org.javers.core.model.DummyUserDetails"
                        cdoId 1
                    }
                    fragment "dummyAddress"
                }
                id 1
            }
        }

        when:
        def snapshot = javersTestAssembly().jsonConverter.fromJson(json.toString(), CdoSnapshot)

        then:
        def valueObjectId = snapshot.getPropertyValue("dummyAddress")
        valueObjectId == GlobalIdTestBuilder.valueObjectId(1, DummyUserDetails, "dummyAddress")
    }

    def "should deserialize CdoSnapshot state with collections"() {

        given:
        def json = new JsonBuilder()
        def ids = [1, 2]
        json {
            commitMetadata {
                id "1.0"
                author "author"
                commitDate "2000-01-01T12:00:00"
            }
            globalId {
                entity "org.javers.core.model.DummyUser"
                cdoId "kaz"
            }
            state {
                intArray 1, 2
                integerList 3, 4
                stringSet "5", "6"
                primitiveMap {
                    time "2000-01-01T12:00:00"
                }
                dummyUserDetailsList ids.collect {
                        id -> [entity: "org.javers.core.model.DummyUserDetails", cdoId: id]
                }
                dateTimes "2000-01-01T12:00:00", "2000-01-01T12:00:00"
                name "kaz"
            }
        }

        when:
        def snapshot = javersTestAssembly().jsonConverter.fromJson(json.toString(), CdoSnapshot)

        then:
        with (snapshot) {
            getPropertyValue("intArray") == [1, 2].toArray()
            getPropertyValue("integerList") == [3, 4]
            getPropertyValue("stringSet") == ["5", "6"] as Set
            getPropertyValue("primitiveMap") == [time: LocalDateTime.of(2000, 1, 1, 12, 0, 0, 0)]

            getPropertyValue("dummyUserDetailsList").size() == 2
            getPropertyValue("dummyUserDetailsList").get(0) == instanceId(1, DummyUserDetails)
            getPropertyValue("dummyUserDetailsList").get(1) == instanceId(2, DummyUserDetails)

            getPropertyValue("dateTimes").size() == 2
            getPropertyValue("dateTimes")[0] instanceof LocalDateTime
        }
    }

    CommitMetadata someCommitMetadata(ZonedDateTime now = ZonedDateTime.now()){
        new CommitMetadata("kazik", [:], now.toLocalDateTime(), now.toInstant(), new CommitId(1, 0))
    }
}