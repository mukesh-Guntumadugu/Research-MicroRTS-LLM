package gui.frontend;

public class GameController {
    public static boolean isPaused = false;

    public static void togglePause() {
        isPaused = !isPaused;
        System.out.println(isPaused ? "Game Paused" : "Game Resumed");
    }
}