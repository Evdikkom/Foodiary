package com.example.foodiary.presentation.activity

import android.widget.Toast
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.foodiary.R
import com.example.foodiary.presentation.fragment.DailyNutritionFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DailyNutritionFragment())
                .commit()
        }

        Toast.makeText(this, "MainActivity: onCreate", Toast.LENGTH_SHORT).show()
    }
}
