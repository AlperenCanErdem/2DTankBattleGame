import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.application.Application;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.*;

/**
 * Main class for the Tank Game application.
 * Sets up the game window, initializes the player, walls, UI, and handles the main game loop and input.
 */
public class Main extends Application {
    /** The player's tank */
    PlayerTank playerTank = new PlayerTank();
    /** The main root pane that holds all game nodes */
    Pane root = new Pane();
    /** The main scene of the game */
    Scene scene = new Scene(root, 800, 800);
    /** The pause menu instance */
    PauseMenu pauseMenu = new PauseMenu();
    /** The game over menu instance */
    GameOverMenu gameOverMenu = new GameOverMenu();
    /** List of all tanks in the game, including the player and enemies */
    List<Tank> tanks = new ArrayList<>();
    /** Timeline for spawning enemy tanks periodically */
    private Timeline enemySpawner = new Timeline();
    /**
     * Main method to launch the JavaFX application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
    /**
     * Starts the JavaFX application and sets up all game components:
     * UI, walls, player tank, input handling, game loop, and enemy spawner.
     *
     * @param primaryStage the main stage provided by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setUserData(this); // Store reference for access from menus

        List<Wall> walls = new ArrayList<>();
        root.setStyle("-fx-background-color: black;");
        scene.setFill(Color.BLACK);
        // Add player tank to the scene
        tanks.add(playerTank);
        root.getChildren().add(playerTank.getImageView());
        // Display player information (health, score)
        GameInfo gameInfo = new GameInfo(playerTank);
        root.getChildren().add(gameInfo.getInfoBox());
        // Create and place walls around the edges and inside the map
        double wallSize = 20;
        int numberOfWalls = 40;

        //Top wall
        for (int i = 0; i < numberOfWalls; i++) {
            Wall wallPiece = new Wall("/assets/wall.png", i * wallSize, 0, wallSize);
            walls.add(wallPiece);
        }

        //Right Wall
        for (int i = 0; i < numberOfWalls; i++) {
            Wall wallPiece = new Wall("/assets/wall.png", 800 - wallSize, i * wallSize, wallSize);
            walls.add(wallPiece);
        }

        //Bottom Wall
        for (int i = 0; i < numberOfWalls; i++) {
            Wall wallPiece = new Wall("/assets/wall.png", i * wallSize, 800 - wallSize, wallSize);
            walls.add(wallPiece);
        }

        //Left Wall
        for (int i = 0; i < numberOfWalls; i++) {
            Wall wallPiece = new Wall("/assets/wall.png", 0, i * wallSize, wallSize);
            walls.add(wallPiece);
        }

        // Vertical wall pairs near the bottom
        for (int i = 0; i < 10; i++) {
            Wall wall1 = new Wall("/assets/wall.png", 100, 500 + i * wallSize, wallSize);
            Wall wall2 = new Wall("/assets/wall.png", 100 + wallSize, 500 + i * wallSize, wallSize);
            Wall wall3 = new Wall("/assets/wall.png", 670, 500 + i * wallSize, wallSize);
            Wall wall4 = new Wall("/assets/wall.png", 670 + wallSize, 500 + i * wallSize, wallSize);
            walls.add(wall1);
            walls.add(wall2);
            walls.add(wall3);
            walls.add(wall4);
        }

        // Diagonal wall section
        for (int i = 0; i < 5; i++) {
            Wall wall = new Wall("/assets/wall.png", 140 + i * wallSize, 140 + i * wallSize, wallSize);
            walls.add(wall);
        }
        // Inverse diagonal wall section
        for (int i = 0; i < 5; i++) {
            Wall wall = new Wall("/assets/wall.png", 560 + i * wallSize, 220 - i * wallSize, wallSize);
            walls.add(wall);
        }

        // Horizontal wall section in the middle
        for (int i = 1; i <= 18; i++) {
            Wall wall = new Wall("/assets/wall.png", 200 + i * wallSize, 500, wallSize);
            walls.add(wall);
        }

        // Add all walls to the scene
        for (Wall wall : walls) {
            root.getChildren().add(wall.getNode());
        }

        // Set to track pressed keys
        Set<KeyCode> pressedKeys = new HashSet<>();

        // Handle key press events
        scene.setOnKeyPressed(e -> {
            KeyCode code = e.getCode();
            pressedKeys.add(code);

            // If game is over, only allow restart (R) or exit (ESC)
            if (gameOverMenu.isOver()) {
                if (code == KeyCode.R) {
                    restartGame();
                    gameOverMenu.hide();
                } else if (code == KeyCode.ESCAPE) {
                    System.exit(0);
                }
                return;
            }

            // If game is paused, allow resume (P), restart (R), or exit (ESC)
            if (pauseMenu.isPaused()) {
                if (code == KeyCode.R) {
                    restartGame();
                    pauseMenu.hide();
                } else if (code == KeyCode.ESCAPE) {
                    System.exit(0);
                } else if (code == KeyCode.P) {
                    // Close pause menu and continue
                    pauseMenu.hide();
                    enemySpawner.play();
                    playerTank.getAnimation().play();
                    for (Tank tank : tanks) {
                        if (tank instanceof EnemyTank) {
                            ((EnemyTank) tank).startAI(walls, tanks, root);
                            tank.getAnimation().play();
                        }
                    }
                }
                return;
            }

            // Pause the game
            if (code == KeyCode.P) {
                pauseMenu.show(root);
                enemySpawner.pause();
                playerTank.getAnimation().pause();
                for (Tank tank : tanks) {
                    if (tank instanceof EnemyTank) {
                        ((EnemyTank) tank).stopAI();
                        tank.getAnimation().stop();
                    }
                }
                return;
            }

        });

        // Handle key release events
        scene.setOnKeyReleased(e -> {
            pressedKeys.remove(e.getCode());
        });

        /**
         * Main game loop: handles player movement and shooting based on pressed keys.
         * Called every frame by JavaFX's AnimationTimer.
         */
        AnimationTimer gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (gameOverMenu.isOver() || pauseMenu.isPaused()) {
                    return;
                }

                boolean up = pressedKeys.contains(KeyCode.UP);
                boolean down = pressedKeys.contains(KeyCode.DOWN);
                boolean left = pressedKeys.contains(KeyCode.LEFT);
                boolean right = pressedKeys.contains(KeyCode.RIGHT);

                // Move player based on key input UP → DOWN → LEFT → RIGHT
                if (up) {
                    playerTank.moveUp(walls, tanks, root);
                } else if (down) {
                    playerTank.moveDown(walls, tanks,root);
                } else if (left) {
                    playerTank.moveLeft(walls, tanks, root);
                } else if (right) {
                    playerTank.moveRight(walls, tanks, root);
                }

                // Handle shooting
                if (pressedKeys.contains(KeyCode.X)) {
                    playerTank.shoot(walls, tanks, root);
                }
            }
        };
        gameLoop.start();

        // Enemy tank spawning every 5 seconds
        enemySpawner = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            EnemyTank.spawnEnemy(root, tanks, walls);
        }));
        enemySpawner.setCycleCount(Timeline.INDEFINITE);
        enemySpawner.play();

        // Add menus to the scene
        pauseMenu = new PauseMenu();
        root.getChildren().add(pauseMenu.getMenuBox());

        gameOverMenu = new GameOverMenu();
        root.getChildren().add(gameOverMenu.getMenuBox());

        // Show the stage
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Restarts the game by resetting the player tank, clearing all tanks,
     * reinitializing the game info, and restarting the enemy spawn timer.
     */
    private void restartGame() {
        // Hide game over and pause menus
        gameOverMenu.hide();
        pauseMenu.hide();

        // Remove all tanks from the scene and list
        for (Iterator<Tank> it = tanks.iterator(); it.hasNext();) {
            Tank tank = it.next();
            root.getChildren().remove(tank.getImageView());
            it.remove();
        }

        // Create a new player tank and add it to the scene
        playerTank = new PlayerTank();
        tanks.add(playerTank);
        root.getChildren().add(playerTank.getImageView());

        // Locate the GameInfo instance and reset score and lives
        GameInfo gameInfo = playerTank.findGameInfo(root);
        if (gameInfo != null) {
            gameInfo.update(0, 3);
        }

        // Restart the enemy spawn timeline
        enemySpawner.stop();
        enemySpawner.play();
    }

    /**
     * Displays the game over screen with the final score,
     * stops all tank animations and enemy AI, and halts enemy spawning.
     *
     * @param finalScore The player's final score to be shown on the game over screen.
     */
    public void showGameOver(int finalScore) {
        // Stop enemy spawning and player's animation
        enemySpawner.stop();
        playerTank.getAnimation().stop();

        // Stop all enemy AI logic
        for (Tank tank : tanks) {
            if (tank instanceof EnemyTank) {
                ((EnemyTank) tank).stopAI();
            }
        }

        // Show game over menu and display final score
        gameOverMenu.show(root);
        gameOverMenu.updateScore(finalScore);
    }
}

/**
 * Represents a generic tank in the game with basic properties such as health, speed, and animation.
 * Provides core functionalities like health reduction, collision detection, and explosion effects.
 */
class Tank {
    // Tank properties
    String type;
    Integer speed;
    Integer health;
    ImageView image;
    ImageView bullet;
    int score = 0;
    long lastShotTime = 0;

    Image[] tankImages;
    int currentImageIndex = 0;
    Timeline animation;

    /**
     * Constructs a Tank with given image paths and health value.
     *
     * @param tankPhotoDirection1 Path to the first directional tank image.
     * @param tankPhotoDirection2 Path to the second directional tank image.
     * @param health Initial health of the tank.
     */
    Tank(String tankPhotoDirection1, String tankPhotoDirection2, Integer health) {
        this.type = "Tank";
        this.speed = 2;
        this.health = health;

        tankImages = new Image[2];
        tankImages[0] = new Image(getClass().getResourceAsStream(tankPhotoDirection1));
        tankImages[1] = new Image(getClass().getResourceAsStream(tankPhotoDirection2));

        image = new ImageView(tankImages[0]);
        image.setFitWidth(40);
        image.setFitHeight(40);
        image.setX(400);
        image.setY(600);

        Image bulletImg = new Image(getClass().getResourceAsStream("/assets/bullet.png"));
        this.bullet = new ImageView(bulletImg);
        bullet.setVisible(false);

        animation = new Timeline(new KeyFrame(Duration.millis(300), e -> {
            currentImageIndex = (currentImageIndex + 1) % tankImages.length;
            image.setImage(tankImages[currentImageIndex]);
        }));
    }

    /**
     * Returns the ImageView representing the tank.
     *
     * @return ImageView of the tank.
     */
    public ImageView getImageView() {
        return image;
    }

    /**
     * Returns the animation used for the tank image switching.
     *
     * @return Animation timeline.
     */
    Animation getAnimation() {
        return animation;
    }

    /**
     * Reduces the health of the tank. If it is a player tank and health reaches zero,
     * triggers game over.
     *
     * @param root The root pane where game nodes are placed.
     */
    private void reduceHealth(Pane root) {
        health -= 1;
        if (this instanceof PlayerTank) {
            GameInfo gameInfo = findGameInfo(root);
            if (gameInfo != null) {
                gameInfo.update(score, health);
            }
            if (health <= 0) {
                Main main = (Main) root.getScene().getWindow().getUserData();
                main.showGameOver(score);
            }
        }
    }

    /**
     * Searches and returns the GameInfo component from the UI.
     *
     * @param root The root pane where game nodes are placed.
     * @return The GameInfo instance, or null if not found.
     */
    public GameInfo findGameInfo(Pane root) {
        for (javafx.scene.Node node : root.getChildren()) {
            if (node instanceof VBox && node.getUserData() instanceof GameInfo) {
                return (GameInfo) node.getUserData();
            }
        }
        return null;
    }

    /**
     * Shows a small explosion effect at the given coordinates.
     *
     * @param root The root pane.
     * @param x X-coordinate of the explosion.
     * @param y Y-coordinate of the explosion.
     */
    private void showSmallExplosion(Pane root, double x, double y) {
        Image explosionImg = new Image(getClass().getResourceAsStream("/assets/smallExplosion.png"));
        ImageView explosion = new ImageView(explosionImg);
        explosion.setFitWidth(30);
        explosion.setFitHeight(30);
        explosion.setX(x - 15);
        explosion.setY(y - 15);

        root.getChildren().add(explosion);

        Timeline removeExplosion = new Timeline(new KeyFrame(Duration.millis(300), e -> {
            root.getChildren().remove(explosion);
        }));
        removeExplosion.setCycleCount(1);
        removeExplosion.play();
    }

    /**
     * Shows a big explosion effect at the given coordinates.
     *
     * @param root The root pane.
     * @param x X-coordinate of the explosion.
     * @param y Y-coordinate of the explosion.
     */
    private void showBigExplosion(Pane root, double x, double y) {
        Image explosionImg = new Image(getClass().getResourceAsStream("/assets/explosion.png"));
        ImageView explosion = new ImageView(explosionImg);
        explosion.setFitWidth(40);
        explosion.setFitHeight(40);
        explosion.setX(x - 20);
        explosion.setY(y - 20);

        root.getChildren().add(explosion);

        Timeline removeExplosion = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            root.getChildren().remove(explosion);
        }));
        removeExplosion.setCycleCount(1);
        removeExplosion.play();
    }

    /**
     * Checks if the tank collides with any wall in the provided list.
     *
     * @param walls List of walls.
     * @return True if collision is detected, otherwise false.
     */
    private boolean collidesWithWall(List<Wall> walls) {
        for (Wall wall : walls) {
            if (image.getBoundsInParent().intersects(wall.getBounds())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the tank collides with another tank (Player or Enemy), triggers
     * explosion effects, and handles health and respawn logic accordingly.
     *
     * @param tanks List of tanks in the game.
     * @param root The root pane of the scene.
     * @return True if collision occurred, otherwise false.
     */
    private boolean collidesWithPlayerOrEnemy(List<Tank> tanks, Pane root) {
        Iterator<Tank> iterator = tanks.iterator();
        while (iterator.hasNext()) {
            Tank tank = iterator.next();

            if (tank == this) continue;

            if (tank.health <= 0) continue;

            if ((this instanceof PlayerTank && tank instanceof EnemyTank) ||
                    (this instanceof EnemyTank && tank instanceof PlayerTank)) {
                if (image.getBoundsInParent().intersects(tank.getImageView().getBoundsInParent())) {
                    double collisionX = (this.image.getX() + tank.image.getX()) / 2;
                    double collisionY = (this.image.getY() + tank.image.getY()) / 2;
                    showBigExplosion(root, collisionX, collisionY);

                    if (this instanceof PlayerTank) {
                        this.reduceHealth(root); // Reduce Player Health
                        this.image.setVisible(false);

                        if (this.health > 0) {
                            this.image.setX(400);
                            this.image.setY(600);
                            PauseTransition pause = new PauseTransition(Duration.seconds(1));
                            pause.setOnFinished(event -> {
                                this.image.setVisible(true);
                                this.image.setX(400);
                                this.image.setY(600);
                            });
                            pause.play();
                        }
                        else {
                            root.getChildren().remove(this.image);
                        }

                        // Remove enemy tank safely
                        root.getChildren().remove(tank.getImageView());
                        iterator.remove();

                    } else if (this instanceof EnemyTank) {
                        // Remove this enemy tank safely
                        root.getChildren().remove(this.getImageView());
                        iterator.remove();

                        // Reduce Player Health
                        tank.reduceHealth(root);

                        if (tank.health > 0) {
                            tank.image.setVisible(false);
                            tank.image.setX(400);
                            tank.image.setY(600);
                            PauseTransition pause = new PauseTransition(Duration.seconds(1));
                            pause.setOnFinished(event -> {
                                tank.image.setVisible(true);
                                tank.image.setX(400);
                                tank.image.setY(600);
                            });
                            pause.play();
                        }
                        else {
                            root.getChildren().remove(this.getImageView());
                            root.getChildren().remove(tank.getImageView());
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Moves the tank upward by its speed.
     * If a collision with a wall or another tank occurs, the move is reverted.
     *
     * @param walls The list of all wall objects in the scene.
     * @param tanks The list of all tank objects in the game.
     * @param root  The root pane containing all game elements.
     */
    void moveUp(List<Wall> walls, List<Tank> tanks, Pane root) {
        image.setY(image.getY() - speed);
        if (collidesWithWall(walls) || collidesWithPlayerOrEnemy(tanks, root)) {
            image.setY(image.getY() + speed);
        }
        image.setRotate(270);
        animation.play();
    }

    /**
     * Moves the tank downward by its speed.
     * If a collision with a wall or another tank occurs, the move is reverted.
     *
     * @param walls The list of all wall objects in the scene.
     * @param tanks The list of all tank objects in the game.
     * @param root  The root pane containing all game elements.
     */
    void moveDown(List<Wall> walls, List<Tank> tanks, Pane root) {
        image.setY(image.getY() + speed);
        if (collidesWithWall(walls) || collidesWithPlayerOrEnemy(tanks, root)) {
            image.setY(image.getY() - speed);
        }
        image.setRotate(90);
        animation.play();
    }

    /**
     * Moves the tank leftward by its speed.
     * If a collision with a wall or another tank occurs, the move is reverted.
     *
     * @param walls The list of all wall objects in the scene.
     * @param tanks The list of all tank objects in the game.
     * @param root  The root pane containing all game elements.
     */
    void moveLeft(List<Wall> walls, List<Tank> tanks, Pane root) {
        image.setX(image.getX() - speed);
        if (collidesWithWall(walls) || collidesWithPlayerOrEnemy(tanks, root)) {
            image.setX(image.getX() + speed);
        }
        image.setRotate(180);
        animation.play();
    }

    /**
     * Moves the tank rightward by its speed.
     * If a collision with a wall or another tank occurs, the move is reverted.
     *
     * @param walls The list of all wall objects in the scene.
     * @param tanks The list of all tank objects in the game.
     * @param root  The root pane containing all game elements.
     */
    void moveRight(List<Wall> walls, List<Tank> tanks, Pane root) {
        image.setX(image.getX() + speed);
        if (collidesWithWall(walls) || collidesWithPlayerOrEnemy(tanks, root)) {
            image.setX(image.getX() - speed);
        }
        image.setRotate(0);
        animation.play();
    }

    /**
     * Fires a bullet from the tank in the current direction.
     * The bullet damages other tanks and disappears upon hitting a wall or leaving the screen.
     *
     * @param walls The list of all wall objects for collision detection.
     * @param tanks The list of all tank objects for target detection.
     * @param root  The root pane to which the bullet is added.
     */
    void shoot(List<Wall> walls, List<Tank> tanks, Pane root) {
        if (image.getParent() == null || health <= 0) return;
        if (lastShotTime + 300 < System.currentTimeMillis()) {
            lastShotTime = System.currentTimeMillis();

            Image bulletImg = new Image(getClass().getResourceAsStream("/assets/bullet.png"));
            ImageView newBullet = new ImageView(bulletImg);
            newBullet.setFitWidth(10);
            newBullet.setFitHeight(10);
            newBullet.setX(image.getX() + image.getFitWidth() / 2 - 5);
            newBullet.setY(image.getY() + image.getFitHeight() / 2 - 5);
            newBullet.setRotate(image.getRotate());

            Pane parent = (Pane) image.getParent();
            parent.getChildren().add(newBullet);

            final Timeline[] bulletAnimation = new Timeline[1];

            bulletAnimation[0] = new Timeline(new KeyFrame(Duration.millis(10), e -> {
                double angle = Math.toRadians(newBullet.getRotate());
                double dx = 5 * Math.cos(angle);
                double dy = 5 * Math.sin(angle);

                newBullet.setX(newBullet.getX() + dx);
                newBullet.setY(newBullet.getY() + dy);

                for (Wall wall : walls) {
                    if (newBullet.getBoundsInParent().intersects(wall.getBounds())) {
                        parent.getChildren().remove(newBullet);
                        showSmallExplosion(parent, newBullet.getX(), newBullet.getY());
                        bulletAnimation[0].stop();
                        return;
                    }
                }

                Iterator<Tank> iterator = tanks.iterator();
                while (iterator.hasNext()) {
                    Tank tank = iterator.next();
                    if (tank == this) continue;

                    if (this instanceof EnemyTank && tank instanceof EnemyTank) {
                        continue;
                    }

                    if (newBullet.getBoundsInParent().intersects(tank.getImageView().getBoundsInParent())) {
                        parent.getChildren().remove(newBullet);
                        bulletAnimation[0].stop();
                        tank.image.setVisible(false);
                        tank.reduceHealth(root);
                        showBigExplosion(parent, newBullet.getX(), newBullet.getY());

                        if (tank instanceof PlayerTank) {
                            tank.image.setX(400);
                            tank.image.setY(600);
                            PauseTransition pause = new PauseTransition(Duration.seconds(1));
                            pause.setOnFinished(event ->{
                            tank.image.setVisible(true);
                            tank.image.setX(400);
                            tank.image.setY(600);
                            });
                            pause.play();
                        }

                        if (tank.health == 0) {
                            parent.getChildren().remove(tank.getImageView());
                            iterator.remove();
                            score += 100;
                            if (this instanceof PlayerTank) {
                                GameInfo gameInfo = findGameInfo(parent);
                                if (gameInfo != null) {
                                    gameInfo.update(score, health);
                                }
                            }
                        }
                        return;
                    }
                }

                if (newBullet.getX() < 0 || newBullet.getX() > parent.getWidth() ||
                        newBullet.getY() < 0 || newBullet.getY() > parent.getHeight()) {
                    parent.getChildren().remove(newBullet);
                    bulletAnimation[0].stop();
                }
            }));

            bulletAnimation[0].setCycleCount(Timeline.INDEFINITE);
            bulletAnimation[0].play();
        }
    }
}

/**
 * Represents the player-controlled tank in the game.
 * Inherits behavior from the base Tank class.
 */
class PlayerTank extends Tank {
    /**
     * Constructs a PlayerTank with predefined images and health.
     */
    PlayerTank() {
        super("/assets/yellowTank1.png", "/assets/yellowTank2.png", 3);
    }
}

/**
 * Represents an enemy-controlled tank in the game.
 * Includes simple AI logic for movement and shooting.
 */
class EnemyTank extends Tank {
    private int currentDirection = 0;
    private int movementTarget = 0;
    private int movementCounter = 0;
    private Timeline aiTimeline;
    private Random random = new Random();
    /**
     * Constructs an EnemyTank with predefined images and reduced speed.
     */
    EnemyTank() {
        super("/assets/whiteTank1.png", "/assets/whiteTank2.png", 1);
        speed = 1;
    }

    /**
     * Spawns a new enemy tank at a random non-colliding position in the game field.
     *
     * @param root  The Pane to which the enemy tank will be added.
     * @param tanks The list of all tanks in the game.
     * @param walls The list of wall objects for collision detection.
     * @return A newly created and initialized EnemyTank object.
     */
    public static EnemyTank spawnEnemy(Pane root, List<Tank> tanks, List<Wall> walls) {
        EnemyTank enemy = new EnemyTank();
        Random rand = new Random();
        double x, y;
        boolean collides;

        // Try random positions until a non-colliding one is found
        do {
            x = rand.nextDouble() * 800;
            y = rand.nextDouble() * 500;

            enemy.getImageView().setX(x);
            enemy.getImageView().setY(y);

            collides = false;
            for (Wall wall : walls) {
                if (enemy.getImageView().getBoundsInParent().intersects(wall.getBounds())) {
                    collides = true;
                    break;
                }
            }
            for (Tank tank : tanks) {
                if(enemy.getImageView().getBoundsInParent().intersects(tank.getImageView().getBoundsInParent())) {
                    collides = true;
                    break;
                }
            }
        } while (collides);

        root.getChildren().add(enemy.getImageView());
        tanks.add(enemy);

        enemy.startAI(walls, tanks, root);

        return enemy;
    }

    /**
     * Starts the AI logic for automatic movement and shooting.
     * The tank moves in random directions and occasionally fires bullets.
     *
     * @param walls The list of wall objects for collision detection.
     * @param tanks The list of all tanks in the game.
     * @param root  The Pane containing the game scene.
     */
    public void startAI(List<Wall> walls, List<Tank> tanks, Pane root) {
        aiTimeline = new Timeline(new KeyFrame(Duration.millis(10), e -> {
            if (movementCounter <= 0){
                currentDirection = random.nextInt(4); // Random direction (0-3)
                movementTarget = 10 + random.nextInt(100);
                movementCounter = movementTarget;
            }
            // Perform movement based on chosen direction
            switch (currentDirection) {
                case 0: moveUp(walls, tanks, root); break;
                case 1: moveDown(walls, tanks, root); break;
                case 2: moveLeft(walls, tanks, root); break;
                case 3: moveRight(walls, tanks, root); break;
            }
            movementCounter --;

            // Occasionally shoot
            if (random.nextInt(100) < 2) {
                shoot(walls, tanks, root);
            }
        }));
        aiTimeline.setCycleCount(Timeline.INDEFINITE);
        aiTimeline.play();
    }

    /**
     * Stops the enemy tank's AI behavior, halting movement and shooting.
     */
    public void stopAI() {
        if (aiTimeline != null) {
            aiTimeline.stop();
        }
    }
}

/**
 * Represents a wall object in the game.
 * Used for collision detection and visual blocking.
 */
class Wall {
    ImageView image;

    /**
     * Constructs a Wall object with a given image and position.
     *
     * @param imagePath Path to the wall image resource.
     * @param x         The x-coordinate of the wall.
     * @param y         The y-coordinate of the wall.
     * @param size      The width and height of the wall.
     */
    public Wall(String imagePath, double x, double y, double size) {
        Image img = new Image(getClass().getResourceAsStream(imagePath));
        image = new ImageView(img);
        image.setFitWidth(size);
        image.setFitHeight(size);
        image.setX(x);
        image.setY(y);
    }

    /**
     * Returns the ImageView node of the wall.
     *
     * @return The ImageView representing the wall.
     */
    public ImageView getNode() {
        return image;
    }

    /**
     * Returns the bounds of the wall in the game scene.
     *
     * @return Bounds for collision detection.
     */
    public Bounds getBounds() {
        return image.getBoundsInParent();
    }
}

/**
 * Displays real-time game information such as score and lives.
 */
class GameInfo {
    private final Label scoreLabel;
    private final Label livesLabel;
    private final VBox infoBox;

    /**
     * Constructs the GameInfo UI based on the player's current status.
     *
     * @param playerTank The player tank used to initialize health display.
     */
    public GameInfo(PlayerTank playerTank) {
        scoreLabel = new Label("Score: 0");
        livesLabel = new Label("Lives: " + playerTank.health);
        scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        livesLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        scoreLabel.setTextFill(Color.WHITE);
        livesLabel.setTextFill(Color.WHITE);

        infoBox = new VBox(5, scoreLabel, livesLabel);
        infoBox.setPadding(new Insets(10));
        infoBox.setLayoutX(20);
        infoBox.setLayoutY(20);
        infoBox.setUserData(this);
    }

    /**
     * Updates the displayed score and lives.
     *
     * @param score The current score.
     * @param lives The current number of lives.
     */
    public void update(int score, int lives) {
        scoreLabel.setText("Score: " + score);
        livesLabel.setText("Lives: " + lives);
    }

    /**
     * Returns the UI container for the game info.
     *
     * @return VBox containing the score and lives labels.
     */
    public VBox getInfoBox() {
        return infoBox;
    }
}

/**
 * Displays the "Game Over" screen when the player loses.
 * Includes options to restart or quit the game.
 */
class GameOverMenu {
    private final VBox menuBox;
    private final Label scoreLabel;
    private boolean isOver;

    /**
     * Constructs the Game Over menu with predefined layout and labels.
     */
    GameOverMenu() {
        Label gameOverLabel = new Label("GAME OVER!");
        scoreLabel = new Label("Score: 0");
        Label restartLabel = new Label("Press [R] to restart");
        Label quitLabel = new Label("Press [ESC] to quit");

        gameOverLabel.setFont(Font.font("Arial", FontWeight.BOLD, 70));
        scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 40));
        restartLabel.setFont(Font.font("Arial", FontWeight.BOLD, 40));
        quitLabel.setFont(Font.font("Arial", FontWeight.BOLD, 40));

        gameOverLabel.setTextFill(Color.RED);
        scoreLabel.setTextFill(Color.RED);
        restartLabel.setTextFill(Color.RED);
        quitLabel.setTextFill(Color.RED);

        menuBox = new VBox(10, gameOverLabel, scoreLabel, restartLabel, quitLabel);
        menuBox.setAlignment(Pos.CENTER);
        menuBox.setPadding(new Insets(20));
        menuBox.setLayoutX(160);
        menuBox.setLayoutY(220);
        menuBox.setVisible(false);
    }

    /**
     * Shows the Game Over menu on the screen.
     *
     * @param root The root pane to which the menu will be added.
     */
    public void show(Pane root) {
        if (!root.getChildren().contains(menuBox)) {
            root.getChildren().add(menuBox);
        }
        menuBox.toFront();
        menuBox.setVisible(true);
        isOver = true;
    }

    /**
     * Hides the Game Over menu.
     */
    public void hide() {
        menuBox.setVisible(false);
        isOver = false;
    }

    /**
     * Checks if the game is currently in a game over state.
     *
     * @return True if game is over, otherwise false.
     */
    public boolean isOver() {
        return isOver;
    }

    /**
     * Returns the Game Over menu UI container.
     *
     * @return VBox representing the menu.
     */
    public VBox getMenuBox() {
        return menuBox;
    }

    /**
     * Updates the score label in the Game Over menu.
     *
     * @param score The final score to display.
     */
    public void updateScore(int score) {
        scoreLabel.setText("Score: " + score);
    }
}

/**
 * Displays the pause menu when the game is paused.
 * Allows continuing, restarting, or quitting the game.
 */
class PauseMenu {
    private final VBox menuBox;
    private boolean isPaused = false;

    /**
     * Constructs the Pause menu with UI elements and layout.
     */
    PauseMenu() {
        Label pausedLabel = new Label("GAME PAUSED!");
        Label continueLabel = new Label("Press [P] to continue");
        Label restartLabel = new Label("Press [R] to restart");
        Label quitLabel = new Label("Press [ESC] to quit");

        pausedLabel.setFont(Font.font("Arial", FontWeight.BOLD, 70));
        continueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 40));
        restartLabel.setFont(Font.font("Arial", FontWeight.BOLD, 40));
        quitLabel.setFont(Font.font("Arial", FontWeight.BOLD, 40));

        pausedLabel.setTextFill(Color.RED);
        continueLabel.setTextFill(Color.RED);
        restartLabel.setTextFill(Color.RED);
        quitLabel.setTextFill(Color.RED);

        menuBox = new VBox(10, pausedLabel, continueLabel, restartLabel, quitLabel);
        menuBox.setAlignment(Pos.CENTER);
        menuBox.setPadding(new Insets(20));
        menuBox.setLayoutX(110);
        menuBox.setLayoutY(220);
        menuBox.setVisible(false);
    }

    /**
     * Shows the pause menu on the screen.
     *
     * @param root The root pane to which the menu will be added.
     */
    public void show(Pane root) {
        if (!root.getChildren().contains(menuBox)) {
            root.getChildren().add(menuBox);
        }
        menuBox.toFront();
        menuBox.setVisible(true);
        isPaused = true;
    }

    /**
     * Hides the pause menu and resumes the game.
     */
    public void hide() {
        menuBox.setVisible(false);
        isPaused = false;
    }

    /**
     * Checks if the game is currently paused.
     *
     * @return True if paused, otherwise false.
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * Returns the Pause menu UI container.
     *
     * @return VBox representing the pause menu.
     */
    public VBox getMenuBox() {
        return menuBox;
    }
}