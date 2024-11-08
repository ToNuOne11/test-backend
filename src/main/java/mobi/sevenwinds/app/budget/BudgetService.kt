package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.authorId = body.authorId?.let { EntityID(it, AuthorTable) }
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val total = BudgetTable.select { BudgetTable.year eq param.year }.count()

            val query = BudgetTable
                .select { BudgetTable.year eq param.year }
                .limit(param.limit, param.offset)
                .orderBy(BudgetTable.month to SortOrder.ASC, BudgetTable.amount to SortOrder.DESC)

            val data = BudgetEntity.wrapRows(query).map { it.toResponse() }.filter {
                if(param.fio == null) {return@filter true}
                if(it.authorId == null) {return@filter false}
                return@filter it.authorFIO!!.toLowerCase().contains(param.fio.toLowerCase())
            }

            val sumByType = BudgetTable
                .slice(BudgetTable.type, BudgetTable.amount.sum())
                .select { BudgetTable.year eq param.year }
                .groupBy(BudgetTable.type)
                .associate { it[BudgetTable.type].name to it[BudgetTable.amount.sum()]!! }

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = data
            )
        }
    }
}