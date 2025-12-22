package com.example.foodiary.presentation.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.foodiary.R
import com.example.foodiary.presentation.fragment.DailyNutritionFragment

/**
 * MainActivity — главный контейнер приложения Foodiary.
 * Отвечает за отображение корневых экранов приложения.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    DailyNutritionFragment()
                )
                .commit()
        }
    }
}
