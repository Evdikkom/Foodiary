package com.example.foodiary.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.foodiary.data.local.dao.AllergenDao
import com.example.foodiary.data.local.dao.FoodAllergenDao
import com.example.foodiary.data.local.dao.FoodDao
import com.example.foodiary.data.local.dao.MealDao
import com.example.foodiary.data.local.dao.RecipeDao
import com.example.foodiary.data.local.dao.UserDao
import com.example.foodiary.data.local.dao.UserRestrictionDao
import com.example.foodiary.data.local.entity.AllergenEntity
import com.example.foodiary.data.local.entity.FoodAllergenEntity
import com.example.foodiary.data.local.entity.FoodEntity
import com.example.foodiary.data.local.entity.MealEntity
import com.example.foodiary.data.local.entity.RecipeEntity
import com.example.foodiary.data.local.entity.RecipeIngredientEntity
import com.example.foodiary.data.local.entity.UserEntity
import com.example.foodiary.data.local.entity.UserRestrictionEntity
import com.example.foodiary.data.local.seed.AllergenCatalog
import com.example.foodiary.data.local.seed.SeedFoodCatalog
import com.example.foodiary.data.model.AllergenEvidenceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AllergenEntity::class,
        FoodAllergenEntity::class,
        FoodEntity::class,
        MealEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        UserEntity::class,
        UserRestrictionEntity::class,
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun allergenDao(): AllergenDao
    abstract fun foodAllergenDao(): FoodAllergenDao
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun recipeDao(): RecipeDao
    abstract fun userDao(): UserDao
    abstract fun userRestrictionDao(): UserRestrictionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        private var SEED_STARTED: Boolean = false

        fun getInstance(context: Context): AppDatabase {
            val appContext = context.applicationContext

            val db = INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "foodiary.db"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { created ->
                        INSTANCE = created
                    }
            }

            ensureSeed(db)
            return db
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                        id TEXT NOT NULL,
                        biologicalSex TEXT NOT NULL,
                        age INTEGER NOT NULL,
                        weightKg REAL NOT NULL,
                        heightCm INTEGER NOT NULL,
                        bodyFatPercent REAL,
                        goal TEXT NOT NULL,
                        activityLevel TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS allergens (
                        id TEXT NOT NULL,
                        code TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        description TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS food_allergens (
                        foodId TEXT NOT NULL,
                        allergenId TEXT NOT NULL,
                        presenceType TEXT NOT NULL,
                        evidenceType TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        PRIMARY KEY(foodId, allergenId, evidenceType)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_restrictions (
                        id TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        allergenId TEXT NOT NULL,
                        restrictionKind TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                AllergenCatalog.allergens.forEach { allergen ->
                    database.execSQL(
                        """
                        INSERT OR REPLACE INTO allergens (id, code, displayName, description, sortOrder)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf(
                            allergen.id,
                            allergen.code,
                            allergen.displayName,
                            allergen.description,
                            allergen.sortOrder
                        )
                    )
                }

                AllergenCatalog.seedFoodAllergens.forEach { (foodId, allergenPairs) ->
                    allergenPairs.forEach { (allergenId, presenceType) ->
                        database.execSQL(
                            """
                            INSERT OR REPLACE INTO food_allergens (foodId, allergenId, presenceType, evidenceType, confidence)
                            VALUES (?, ?, ?, ?, ?)
                            """.trimIndent(),
                            arrayOf(
                                foodId,
                                allergenId,
                                presenceType.name,
                                AllergenEvidenceType.MANUAL.name,
                                1.0
                            )
                        )
                    }
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val hasUsersTable = database.hasTable("users")
                if (!hasUsersTable) {
                    createUsersTable(database)
                    return
                }

                val userColumns = database.tableColumns("users")
                val hasCurrentUsersSchema = setOf(
                    "biologicalSex",
                    "weightKg",
                    "heightCm",
                    "bodyFatPercent"
                ).all { it in userColumns }

                if (hasCurrentUsersSchema) return

                database.execSQL("ALTER TABLE users RENAME TO users_legacy")
                createUsersTable(database)

                val idColumn = if ("id" in userColumns) "id" else "'current_user'"
                val biologicalSexColumn = if ("biologicalSex" in userColumns) {
                    "biologicalSex"
                } else {
                    "'FEMALE'"
                }
                val ageColumn = if ("age" in userColumns) "age" else "25"
                val weightColumn = when {
                    "weightKg" in userColumns -> "weightKg"
                    "weight" in userColumns -> "weight"
                    else -> "70.0"
                }
                val heightColumn = when {
                    "heightCm" in userColumns -> "heightCm"
                    "height" in userColumns -> "height"
                    else -> "175"
                }
                val bodyFatColumn = if ("bodyFatPercent" in userColumns) {
                    "bodyFatPercent"
                } else {
                    "NULL"
                }
                val goalColumn = if ("goal" in userColumns) "goal" else "'MAINTAIN_WEIGHT'"
                val activityColumn = if ("activityLevel" in userColumns) {
                    "activityLevel"
                } else {
                    "'LOW_ACTIVE'"
                }

                database.execSQL(
                    """
                    INSERT OR REPLACE INTO users (
                        id,
                        biologicalSex,
                        age,
                        weightKg,
                        heightCm,
                        bodyFatPercent,
                        goal,
                        activityLevel
                    )
                    SELECT
                        COALESCE($idColumn, 'current_user'),
                        COALESCE($biologicalSexColumn, 'FEMALE'),
                        COALESCE($ageColumn, 25),
                        COALESCE($weightColumn, 70.0),
                        COALESCE($heightColumn, 175),
                        $bodyFatColumn,
                        COALESCE($goalColumn, 'MAINTAIN_WEIGHT'),
                        COALESCE($activityColumn, 'LOW_ACTIVE')
                    FROM users_legacy
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE users_legacy")
            }
        }

        private fun createUsersTable(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT NOT NULL,
                    biologicalSex TEXT NOT NULL,
                    age INTEGER NOT NULL,
                    weightKg REAL NOT NULL,
                    heightCm INTEGER NOT NULL,
                    bodyFatPercent REAL,
                    goal TEXT NOT NULL,
                    activityLevel TEXT NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
        }

        private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean {
            query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(tableName)
            ).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun SupportSQLiteDatabase.tableColumns(tableName: String): Set<String> {
            query("PRAGMA table_info($tableName)").use { cursor ->
                val columns = linkedSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) {
                        columns += cursor.getString(nameIndex)
                    }
                }
                return columns
            }
        }

        private fun ensureSeed(db: AppDatabase) {
            if (SEED_STARTED) return
            SEED_STARTED = true

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val allergenDao = db.allergenDao()
                    val foodAllergenDao = db.foodAllergenDao()
                    val foodDao = db.foodDao()

                    allergenDao.insertAll(AllergenCatalog.allergens)

                    val foodsCount = foodDao.countFoods()
                    android.util.Log.d("DB_SEED", "foodsCount(before)=$foodsCount")

                    val seedFoods = buildSeedFoods()

                    foodDao.insertAll(seedFoods)
                    seedFoodAllergens(foodAllergenDao)

                    if (foodsCount == 0) {
                        android.util.Log.d("DB_SEED", "seed inserted OK")
                    } else {
                        android.util.Log.d("DB_SEED", "seed refreshed OK")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DB_SEED", "seed failed: ${e.message}", e)
                }
            }
        }

        private fun buildSeedFoods(): List<FoodEntity> {
            return SeedFoodCatalog.foods
        }

        private suspend fun seedFoodAllergens(foodAllergenDao: FoodAllergenDao) {
            val links = AllergenCatalog.seedFoodAllergens.flatMap { (foodId, allergenPairs) ->
                allergenPairs.map { (allergenId, presenceType) ->
                    FoodAllergenEntity(
                        foodId = foodId,
                        allergenId = allergenId,
                        presenceType = presenceType,
                        evidenceType = AllergenEvidenceType.MANUAL,
                        confidence = 1.0
                    )
                }
            }
            if (links.isNotEmpty()) {
                foodAllergenDao.insertAll(links)
            }
        }
    }
}
