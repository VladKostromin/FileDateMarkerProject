package com.vladkostromin.filedatemarkerproject.controllers;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class MainController {

    @FXML
    private TextField directoryPathField;

    @FXML
    private TextField minDaysField;

    @FXML
    private TextArea outputTextArea;

    @FXML
    private ProgressBar progressBar;

    private File selectedDirectory;
    private Task<Void> currentTask;

    @FXML
    private void handleSelectedDisk() {
        File selectedDisk;

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
        Platform.runLater(() -> outputTextArea.clear());

        currentTask = new Task<>() {
            @Override
            protected Void call() {
                updateMessage("Сканирование началось...\n");

                long currentTime = System.currentTimeMillis();

                long totalFolders = countFolders(directory);
                AtomicLong processedFolders = new AtomicLong(0);

                traverseDirectories(directory, currentTime, totalFolders, processedFolders);

                updateMessage("Сканирование завершено.\n");
                return null;
            }

            private void traverseDirectories(File dir, long currentTime, long totalFolders, AtomicLong processedFolders) {
                if (isCancelled()) {
                    return;
                }

                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (isCancelled()) {
                            return;
                        }
                        if (file.isDirectory()) {
                            try {
                                // Получаем дату создания папки
                                Path path = file.toPath();
                                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

                                FileTime creationTime = attrs.creationTime();
                                long creationMillis = creationTime.toMillis();

                                // Если время создания недоступно или равно 0, используем время последней модификации
                                if (creationMillis == 0L) {
                                    creationMillis = attrs.lastModifiedTime().toMillis();
                                }

                                long daysSinceCreation = (currentTime - creationMillis) / (24 * 60 * 60 * 1000);

                                // Получаем минимальное количество дней из поля ввода
                                long minDays;
                                try {
                                    minDays = Long.parseLong(minDaysField.getText());
                                } catch (NumberFormatException e) {
                                    minDays = 0; // Значение по умолчанию
                                }

                                if (daysSinceCreation >= minDays) {
                                    String message = "Папка: \"" + file.getAbsolutePath() + "\", дней с момента создания: " + daysSinceCreation + "\n";
                                    updateMessage(message);
                                }
                            } catch (IOException e) {
                                String message = "Не удалось получить информацию о папке: " + file.getAbsolutePath() + "\n";
                                updateMessage(message);
                            }

                            // Обновляем прогресс
                            long processed = processedFolders.incrementAndGet();
                            updateProgress(processed, totalFolders);

                            // Рекурсивный вызов для обхода вложенных папок
                            traverseDirectories(file, currentTime, totalFolders, processedFolders);
                        }
                    }
                } else {
                    String message = "Не удалось получить доступ к директории: " + dir.getAbsolutePath() + "\n";
                    updateMessage(message);
                }
            }

            private long countFolders(File dir) {
                if (isCancelled()) {
                    return 0;
                }

                long count = 0;
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (isCancelled()) {
                            return count;
                        }
                        if (file.isDirectory()) {
                            count++;
                            count += countFolders(file);
                        }
                    }
                }
                return count;
            }
        };

        progressBar.progressProperty().bind(currentTask.progressProperty());

        currentTask.messageProperty().addListener((obs, oldMessage, newMessage) -> outputTextArea.appendText(newMessage));

        new Thread(currentTask).start();
    }
}
