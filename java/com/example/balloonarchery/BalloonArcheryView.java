package com.example.balloonarchery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import android.graphics.DashPathEffect;

public class BalloonArcheryView extends View {

    private Paint scorePaint, buttonPaint, buttonTextPaint;
    private int score = 0;
    private int lives = 3;
    private float bowX, bowY;
    private float arrowX, arrowY;
    private float arrowAngle = 0;
    private float touchY;
    private boolean isShooting = false;
    private float arrowSpeed = 20;
    private ArrayList<Balloon> balloons;
    private Random random;
    private boolean gameOver = false;
    private long lastBalloonTime = 0;
    private Bitmap bowBitmap;
    private Bitmap heartFullBitmap;
    private Bitmap heartEmptyBitmap;
    private RectF retryButtonRect;
    private long gameStartTime = 0;
    private static final long SAFETY_DELAY = 500; // 500ms safety delay after restart

    private Paint trajectoryPaint;
    private float[] trajectoryPoints;
    private final int TRAJECTORY_POINTS = 10;
    private final float TRAJECTORY_SEGMENT_LENGTH = 30;

    public BalloonArcheryView(Context context) {
        super(context);
        init();
    }

    public BalloonArcheryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        scorePaint = new Paint();
        scorePaint.setColor(Color.BLACK);
        scorePaint.setTextSize(55);
        scorePaint.setTextAlign(Paint.Align.LEFT);

        buttonPaint = new Paint();
        buttonPaint.setColor(Color.rgb(76, 175, 80)); // Green button

        buttonTextPaint = new Paint();
        buttonTextPaint.setColor(Color.WHITE);
        buttonTextPaint.setTextSize(50);
        buttonTextPaint.setTextAlign(Paint.Align.CENTER);

        random = new Random();
        balloons = new ArrayList<>();
        retryButtonRect = new RectF();
        gameStartTime = System.currentTimeMillis();

        // Load bow bitmap
        bowBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bow);

        // Load heart bitmaps
        heartFullBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.heart_full);
        heartEmptyBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.heart_empty);

        // Resize bow bitmap if it's not null
        if (bowBitmap != null) {
            int targetWidth = 120;
            int targetHeight = 150;
            bowBitmap = Bitmap.createScaledBitmap(bowBitmap, targetWidth, targetHeight, true);
        }

        // Resize heart bitmaps if they're not null
        if (heartFullBitmap != null) {
            heartFullBitmap = Bitmap.createScaledBitmap(heartFullBitmap, 50, 80, true);
        }

        if (heartEmptyBitmap != null) {
            heartEmptyBitmap = Bitmap.createScaledBitmap(heartEmptyBitmap, 50, 80, true);
        }

        // Start the game loop
        post(new Runnable() {
            @Override
            public void run() {
                update();
                invalidate();
                postDelayed(this, 16); // ~60fps
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bowX = 100;
        bowY = h - 200;
        resetArrow();

        trajectoryPaint = new Paint();
        trajectoryPaint.setColor(Color.GRAY);
        trajectoryPaint.setStrokeWidth(3);
        trajectoryPaint.setStyle(Paint.Style.STROKE);
        trajectoryPaint.setPathEffect(new DashPathEffect(new float[] {10, 10}, 0));

        trajectoryPoints = new float[TRAJECTORY_POINTS * 2]; // x,y pairs

        // Initialize retry button
        float buttonWidth = 300;
        float buttonHeight = 100;
        retryButtonRect.set(
                w/2 - buttonWidth/2,
                h/2 + 150,
                w/2 + buttonWidth/2,
                h/2 + 150 + buttonHeight
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background
        canvas.drawColor(Color.rgb(135, 206, 235)); // Sky blue

        // Draw ground
        Paint groundPaint = new Paint();
        groundPaint.setColor(Color.rgb(76, 175, 80));
        canvas.drawRect(0, getHeight() - 150, getWidth(), getHeight(), groundPaint);

        // Draw bow
        if (bowBitmap != null) {
            canvas.save();
            canvas.rotate(arrowAngle, bowX, bowY);
            canvas.drawBitmap(bowBitmap, bowX - bowBitmap.getWidth()/2, bowY - bowBitmap.getHeight()/2, null);
            canvas.restore();
        } else {
            // Fallback if bitmap fails to load
            Paint bowPaint = new Paint();
            bowPaint.setColor(Color.RED);
            bowPaint.setStrokeWidth(10);
            bowPaint.setStyle(Paint.Style.STROKE);

            canvas.save();
            canvas.rotate(arrowAngle, bowX, bowY);
            Path bowPath = new Path();
            bowPath.moveTo(bowX - 30, bowY - 60);
            bowPath.quadTo(bowX + 20, bowY, bowX - 30, bowY + 60);
            canvas.drawPath(bowPath, bowPaint);

            // Draw bowstring
            Paint stringPaint = new Paint();
            stringPaint.setColor(Color.WHITE);
            stringPaint.setStrokeWidth(3);
            canvas.drawLine(bowX - 30, bowY - 60, bowX - 30, bowY + 60, stringPaint);
            canvas.restore();
        }

        // Draw trajectory prediction line if not shooting
        if (!isShooting && !gameOver) {
            // Calculate trajectory points
            calculateTrajectory();

            // Draw the trajectory path
            Path trajectoryPath = new Path();
            if (trajectoryPoints.length >= 2) {
                trajectoryPath.moveTo(trajectoryPoints[0], trajectoryPoints[1]);
                for (int i = 2; i < trajectoryPoints.length; i += 2) {
                    trajectoryPath.lineTo(trajectoryPoints[i], trajectoryPoints[i + 1]);
                }
                canvas.drawPath(trajectoryPath, trajectoryPaint);
            }
        }

        // Draw arrow if it's being shot
        if (isShooting) {
            Paint arrowPaint = new Paint();
            arrowPaint.setColor(Color.GRAY);
            arrowPaint.setStrokeWidth(5);

            canvas.save();
            canvas.rotate((float) Math.toDegrees(Math.atan2(arrowY - bowY, arrowX - bowX)), arrowX, arrowY);
            canvas.drawLine(arrowX - 50, arrowY, arrowX, arrowY, arrowPaint);

            // Draw arrowhead
            Paint headPaint = new Paint();
            headPaint.setColor(Color.RED);
            Path arrowHead = new Path();
            arrowHead.moveTo(arrowX, arrowY - 10);
            arrowHead.lineTo(arrowX + 15, arrowY);
            arrowHead.lineTo(arrowX, arrowY + 10);
            arrowHead.close();
            canvas.drawPath(arrowHead, headPaint);
            canvas.restore();
        } else if (!gameOver) {
            // Draw ready arrow
            Paint arrowPaint = new Paint();
            arrowPaint.setColor(Color.GRAY);
            arrowPaint.setStrokeWidth(5);

            canvas.save();
            canvas.rotate(arrowAngle, bowX, bowY);
            canvas.drawLine(bowX, bowY, bowX + 70, bowY, arrowPaint);

            // Draw arrowhead
            Paint headPaint = new Paint();
            headPaint.setColor(Color.RED);
            Path arrowHead = new Path();
            arrowHead.moveTo(bowX + 70, bowY - 10);
            arrowHead.lineTo(bowX + 85, bowY);
            arrowHead.lineTo(bowX + 70, bowY + 10);
            arrowHead.close();
            canvas.drawPath(arrowHead, headPaint);
            canvas.restore();
        }

        // Draw balloons
        for (Balloon balloon : balloons) {
            Paint balloonPaint = new Paint();
            balloonPaint.setColor(balloon.color);

            // Balloon body
            canvas.drawOval(
                    balloon.x - 50,
                    balloon.y - 70,
                    balloon.x + 50,
                    balloon.y + 70,
                    balloonPaint
            );

            // Balloon string
            Paint stringPaint = new Paint();
            stringPaint.setColor(Color.BLACK);
            stringPaint.setStrokeWidth(2);
            canvas.drawLine(balloon.x, balloon.y + 50, balloon.x, balloon.y + 90, stringPaint);
        }

        // Draw score
        canvas.drawText("Score: " + score, 50, 110, scorePaint);

        // Draw hearts for lives
        if (heartFullBitmap != null && heartEmptyBitmap != null) {
            int heartSpacing = 60;
            int startX = getWidth() - 60;
            int startY = 90;

            for (int i = 0; i < 3; i++) {
                if (i < lives) {
                    canvas.drawBitmap(heartFullBitmap, startX - (i * heartSpacing), startY, null);
                } else {
                    canvas.drawBitmap(heartEmptyBitmap, startX - (i * heartSpacing), startY, null);
                }
            }
        } else {
            // Fallback text display if heart images not available
            Paint livesPaint = new Paint();
            livesPaint.setColor(Color.RED);
            livesPaint.setTextSize(55);
            livesPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("Lives: " + lives, getWidth() - 50, 110, livesPaint);
        }

        // Draw game over message and retry button
        if (gameOver) {
            Paint gameOverPaint = new Paint();
            gameOverPaint.setColor(Color.RED);
            gameOverPaint.setTextSize(100);
            gameOverPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("GAME OVER", getWidth()/2, getHeight()/2, gameOverPaint);
            gameOverPaint.setTextSize(60);
            canvas.drawText("Final Score: " + score, getWidth()/2, getHeight()/2 + 100, gameOverPaint);

            // Draw retry button
            canvas.drawRoundRect(retryButtonRect, 15, 15, buttonPaint);
            canvas.drawText("RETRY", retryButtonRect.centerX(), retryButtonRect.centerY() + 15, buttonTextPaint);
        }

        // Optional: Display a "Get Ready" message if within safety period
        if (System.currentTimeMillis() - gameStartTime < SAFETY_DELAY && !gameOver) {
            Paint readyPaint = new Paint();
            readyPaint.setColor(Color.BLACK);
            readyPaint.setTextSize(70);
            readyPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Get Ready...", getWidth()/2, getHeight()/2, readyPaint);
        }
    }

    private void calculateTrajectory() {
        double radians = Math.toRadians(arrowAngle);
        float startX = bowX + (bowBitmap != null ? bowBitmap.getWidth() / 2 : 50);
        float startY = bowY;

        trajectoryPoints[0] = startX;
        trajectoryPoints[1] = startY;

        for (int i = 1; i < TRAJECTORY_POINTS; i++) {
            float distance = i * TRAJECTORY_SEGMENT_LENGTH;
            trajectoryPoints[i * 2] = startX + (float) (Math.cos(radians) * distance);
            trajectoryPoints[i * 2 + 1] = startY + (float) (Math.sin(radians) * distance);
        }
    }

    private void update() {
        if (gameOver) return;

        // Generate new balloons
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBalloonTime > 2000) { // Every 2 seconds
            createBalloon();
            lastBalloonTime = currentTime;
        }

        // Update balloon positions
        Iterator<Balloon> iterator = balloons.iterator();
        while (iterator.hasNext()) {
            Balloon balloon = iterator.next();
            balloon.y -= balloon.speed;

            // Remove balloons that went off screen
            if (balloon.y < -100) {
                iterator.remove();
            }
        }

        // Update arrow position if shooting
        if (isShooting) {
            double radians = Math.toRadians(arrowAngle);
            arrowX += Math.cos(radians) * arrowSpeed;
            arrowY += Math.sin(radians) * arrowSpeed;

            // Check for collisions with balloons
            Iterator<Balloon> balloonIterator = balloons.iterator();
            while (balloonIterator.hasNext()) {
                Balloon balloon = balloonIterator.next();
                if (distance(arrowX, arrowY, balloon.x, balloon.y) < 50) {
                    // Balloon popped!
                    score += 10;
                    balloonIterator.remove();
                    resetArrow();
                    break;
                }
            }

            // Reset arrow if it goes off screen
            if (arrowX > getWidth() || arrowX < 0 || arrowY > getHeight() || arrowY < 0) {
                lives--;
                if (lives <= 0) {
                    gameOver = true;
                }
                resetArrow();
            }
        }
    }

    private void resetArrow() {
        isShooting = false;
        arrowX = bowX + (bowBitmap != null ? bowBitmap.getWidth()/2 : 50);
        arrowY = bowY;
    }

    private void createBalloon() {
        int color = getRandomColor();
        float x = random.nextInt(getWidth() - 100) + 50;
        float y = getHeight() + 50;
        float speed = random.nextFloat() * 3 + 2; // Speed between 2-5
        balloons.add(new Balloon(x, y, color, speed));
    }

    private int getRandomColor() {
        int[] colors = {
                Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
                Color.MAGENTA, Color.CYAN, Color.rgb(255, 165, 0) // Orange
        };
        return colors[random.nextInt(colors.length)];
    }

    private float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        // Handle game over state - check for retry button press
        if (gameOver) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && retryButtonRect.contains(x, y)) {
                resetGame();
                return true;
            }
            return true;
        }

        // Prevent interaction during safety delay after restart
        if (System.currentTimeMillis() - gameStartTime < SAFETY_DELAY) {
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isShooting) {
                    // Calculate angle based on touch position
                    float deltaY = event.getY() - bowY;
                    float deltaX = 100; // Fixed distance for aiming
                    arrowAngle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));

                    // Limit angle to prevent shooting backwards
                    if (arrowAngle > 80) arrowAngle = 80;
                    if (arrowAngle < -80) arrowAngle = -80;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!isShooting) {
                    isShooting = true;
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void resetGame() {
        score = 0;
        lives = 3;
        balloons.clear();
        gameOver = false;
        resetArrow();
        gameStartTime = System.currentTimeMillis(); // Record when the game was restarted
    }

    // Balloon class
    private class Balloon {
        float x, y;
        int color;
        float speed;

        Balloon(float x, float y, int color, float speed) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.speed = speed;
        }
    }
}