package com.co.example;

import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final int[] dieStates = new int[]{
                R.drawable.die_1,
                R.drawable.die_2,
                R.drawable.die_3,
                R.drawable.die_4,
                R.drawable.die_5,
                R.drawable.die_6,
        };

        final Random rand = new Random();
        final Animation anim = AnimationUtils.loadAnimation(this, R.anim.press);

        ImageButton ib = findViewById(R.id.dice);
        ib.setOnClickListener(view -> {
            ib.setBackgroundResource(dieStates[rand.nextInt(6)]);
            ib.startAnimation(anim);
        });
    }
}