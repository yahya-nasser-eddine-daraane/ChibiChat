package com.lanmessenger.ui;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX entry point.
 * Starts on the Login screen; switches to the main chat window after login.
 */
public class ChibiChatApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        stage.setTitle("ChibiChat");
        stage.setMinWidth(800);
        stage.setMinHeight(550);
        stage.setWidth(1000);
        stage.setHeight(650);

        // Start on the login screen
        showLogin();
        stage.show();
    }

    public static void showLogin() {
        LoginScreen login = new LoginScreen(primaryStage);
        primaryStage.setScene(login.buildScene());
        primaryStage.setTitle("ChibiChat — Sign in");
    }

    public static void showMain() {
        MainScreen main = new MainScreen(primaryStage);
        primaryStage.setScene(main.buildScene());
        primaryStage.setTitle("ChibiChat — @" + AppState.get().getUsername());
    }

    @Override
    public void stop() {
        // Clean up on window close
        AppState state = AppState.get();
        if (state.getRouter()    != null) state.getRouter().closeAll();
        if (state.getMsgServer() != null) state.getMsgServer().stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
