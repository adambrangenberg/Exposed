package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import java.math.BigDecimal
import java.util.*

object DMLTestsData {
    object Cities : Table() {
        val id: Column<Int> = integer("cityId").autoIncrement()
        val name: Column<String> = varchar("name", 50)
        override val primaryKey = PrimaryKey(id)
    }

    object Users : Table() {
        val id: Column<String> = varchar("id", 10)
        val name: Column<String> = varchar("name", length = 50)
        val cityId: Column<Int?> = reference("city_id", Cities.id).nullable()
        val flags: Column<Int> = integer("flags").default(0)
        override val primaryKey = PrimaryKey(id)

        object Flags {
            const val IS_ADMIN = 0b1
            const val HAS_DATA = 0b1000
        }
    }

    object UserData : Table() {
        val user_id: Column<String> = reference("user_id", Users.id)
        val comment: Column<String> = varchar("comment", 30)
        val value: Column<Int> = integer("value")
    }

    object Sales : Table() {
        val year: Column<Int> = integer("year")
        val month: Column<Int> = integer("month")
        val product: Column<String?> = varchar("product", 30).nullable()
        val amount: Column<BigDecimal> = decimal("amount", 8, 2)
    }
}

@Suppress("LongMethod")
fun DatabaseTestsBase.withCitiesAndUsers(
    exclude: List<TestDB> = emptyList(),
    statement: Transaction.(
        cities: DMLTestsData.Cities,
        users: DMLTestsData.Users,
        userData: DMLTestsData.UserData
    ) -> Unit
) {
    val Users = DMLTestsData.Users
    val UserFlags = DMLTestsData.Users.Flags
    val Cities = DMLTestsData.Cities
    val UserData = DMLTestsData.UserData

    withTables(exclude, Cities, Users, UserData) {
        val saintPetersburgId = Cities.insert {
            it[name] = "St. Petersburg"
        } get Cities.id

        val munichId = Cities.insert {
            it[name] = "Munich"
        } get Cities.id

        Cities.insert {
            it[name] = "Prague"
        }

        Users.insert {
            it[id] = "andrey"
            it[name] = "Andrey"
            it[cityId] = saintPetersburgId
            it[flags] = UserFlags.IS_ADMIN
        }

        Users.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[cityId] = munichId
            it[flags] = UserFlags.IS_ADMIN or UserFlags.HAS_DATA
        }

        Users.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[cityId] = munichId
            it[flags] = UserFlags.HAS_DATA
        }

        Users.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[cityId] = null
        }

        Users.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[cityId] = null
            it[flags] = UserFlags.HAS_DATA
        }

        UserData.insert {
            it[user_id] = "smth"
            it[comment] = "Something is here"
            it[value] = 10
        }

        UserData.insert {
            it[user_id] = "smth"
            it[comment] = "Comment #2"
            it[value] = 20
        }

        UserData.insert {
            it[user_id] = "eugene"
            it[comment] = "Comment for Eugene"
            it[value] = 20
        }

        UserData.insert {
            it[user_id] = "sergey"
            it[comment] = "Comment for Sergey"
            it[value] = 30
        }

        statement(Cities, Users, UserData)
    }
}

fun DatabaseTestsBase.withSales(
    statement: Transaction.(testDb: TestDB, sales: DMLTestsData.Sales) -> Unit
) {
    val sales = DMLTestsData.Sales

    withTables(sales) {
        insertSale(2018, 11, "tea", "550.10")
        insertSale(2018, 12, "coffee", "1500.25")
        insertSale(2018, 12, "tea", "900.30")
        insertSale(2019, 1, "coffee", "1620.10")
        insertSale(2019, 1, "tea", "650.70")
        insertSale(2019, 2, "coffee", "1870.90")
        insertSale(2019, 2, null, "10.20")

        statement(it, sales)
    }
}

private fun insertSale(year: Int, month: Int, product: String?, amount: String) {
    val sales = DMLTestsData.Sales
    sales.insert {
        it[sales.year] = year
        it[sales.month] = month
        it[sales.product] = product
        it[sales.amount] = BigDecimal(amount)
    }
}

object OrgMemberships : IntIdTable() {
    val orgId = reference("org", Orgs.uid)
}

class OrgMembership(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<OrgMembership>(OrgMemberships)

    val orgId by OrgMemberships.orgId
    var org by Org referencedOn OrgMemberships.orgId
}

object Orgs : IntIdTable() {
    val uid = varchar("uid", 36).uniqueIndex().clientDefault { UUID.randomUUID().toString() }
    val name = varchar("name", 256)
}

class Org(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Org>(Orgs)

    var uid by Orgs.uid
    var name by Orgs.name
}

internal fun Iterable<ResultRow>.toCityNameList(): List<String> = map { it[DMLTestsData.Cities.name] }
