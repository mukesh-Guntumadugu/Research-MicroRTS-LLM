package gui.frontend;

public class GameController {
    public static boolean isPaused = false;

    public static void togglePause() {
        if (isPaused) {
            resume();
        } else {
            pause();
        }
    }
}
