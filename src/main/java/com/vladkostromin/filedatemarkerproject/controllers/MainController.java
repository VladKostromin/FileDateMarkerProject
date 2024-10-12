package com.vladkostromin.filedatemarkerproject.controllers;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

public class MainController {

    @FXML
    private TextField directoryPathField;

    @FXML
    private TextField minDaysField;

    @FXML
    private TextArea outputTextArea;

    @FXML
    private ProgressIndicator progressIndicator;

    private File selectedDirectory;
    private Task<Void> currentTask;

    @FXML
    private void handleSelectedDisk() {
        File selectedDisk = null;

        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            // Для Windows
            selectedDisk = selectDiskWindows();
        } else if (osName.contains("mac")) {
            // Для macOS
            selectedDisk = selectDiskMac();
        } else {
            outputTextArea.appendText("Операционная система не поддерживается.\n");
            return;
        }

        if (selectedDisk != null) {
            selectedDirectory = selectedDisk;
            directoryPathField.setText(selectedDirectory.getAbsolutePath());
            outputTextArea.appendText("Диск выбран: " + selectedDirectory.getAbsolutePath() + "\n");
        } else {
            outputTextArea.appendText("Диск не выбран.\n");
        }
    }

    private File selectDiskWindows() {
        List<File> roots = Arrays.asList(File.listRoots());

        ChoiceDialog<File> dialog = new ChoiceDialog<>(roots.get(0), roots);
        dialog.setTitle("Выбор диска");
        dialog.setHeaderText("Выберите диск для сканирования");
        dialog.setContentText("Диск:");

        Optional<File> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private File selectDiskMac() {
        File volumesDir = new File("/Volumes");
        File[] volumes = volumesDir.listFiles(File::isDirectory);

        if (volumes != null && volumes.length > 0) {
            ChoiceDialog<File> dialog = new ChoiceDialog<>(volumes[0], volumes);
            dialog.setTitle("Выбор диска");
            dialog.setHeaderText("Выберите диск для сканирования");
            dialog.setContentText("Диск:");

            Optional<File> result = dialog.showAndWait();
            return result.orElse(null);
        } else {
            outputTextArea.appendText("Не удалось найти подключенные диски.\n");
            return null;
        }
    }

    @FXML
    private void handleScanAndMark() {
        if (selectedDirectory != null) {
            processFiles(selectedDirectory);
        } else {
            outputTextArea.appendText("Пожалуйста, выберите диск перед сканированием.\n");
        }
    }

    @FXML
    private void handleCancel() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            outputTextArea.appendText("Сканирование прервано пользователем.\n");
        }
    }

    private void processFiles(File directory) {
        // Очищаем outputTextArea перед началом сканирования
        Platform.runLater(() -> {
            outputTextArea.clear();
            progressIndicator.setVisible(true);
        });

        currentTask = new Task<>() {
            @Override
            protected Void call() {
                updateMessage("Сканирование началось...\n");

                try {
                    long currentTime = System.currentTimeMillis();

                    // Получаем минимальное количество дней
                    long minDays = 0;
                    try {
                        minDays = Long.parseLong(minDaysField.getText());
                    } catch (NumberFormatException e) {
                        minDays = 0; // Значение по умолчанию
                    }

                    List<String> results = Collections.synchronizedList(new ArrayList<>());

                    int availableProcessors = Runtime.getRuntime().availableProcessors();
                    ForkJoinPool forkJoinPool = new ForkJoinPool(availableProcessors);
                    FolderScannerTask rootTask = new FolderScannerTask(directory, currentTime, minDays, results, this);

                    forkJoinPool.invoke(rootTask);

                    if (isCancelled()) {
                        updateMessage("Сканирование было отменено.\n");
                    } else {
                        // После завершения сканирования обновляем интерфейс
                        for (String message : results) {
                            updateMessage(message);
                        }

                        updateMessage("Сканирование завершено.\n");
                    }
                } catch (Exception e) {
                    String errorMessage = "Ошибка во время сканирования: " + e.getMessage() + "\n";
                    updateMessage(errorMessage);
                    e.printStackTrace();
                } finally {
                    Platform.runLater(() -> progressIndicator.setVisible(false));
                }
                return null;
            }
        };

        currentTask.setOnFailed(event -> {
            Throwable ex = currentTask.getException();
            String errorMessage = "Задача завершилась с ошибкой: " + ex.getMessage() + "\n";
            Platform.runLater(() -> {
                outputTextArea.appendText(errorMessage);
                progressIndicator.setVisible(false);
            });
            ex.printStackTrace();
        });

        currentTask.messageProperty().addListener((obs, oldMessage, newMessage) -> {
            Platform.runLater(() -> outputTextArea.appendText(newMessage));
        });

        new Thread(currentTask).start();
    }

    private void updateMessage(String message) {
        Platform.runLater(() -> outputTextArea.appendText(message));
    }
}
