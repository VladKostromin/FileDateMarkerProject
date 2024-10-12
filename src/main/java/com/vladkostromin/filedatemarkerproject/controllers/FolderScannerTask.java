package com.vladkostromin.filedatemarkerproject.controllers;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

import javafx.concurrent.Task;

public class FolderScannerTask extends RecursiveAction {
    private final File directory;
    private final long currentTime;
    private final long minDays;
    private final List<String> results;
    private final Task<?> parentTask;

    public FolderScannerTask(File directory, long currentTime, long minDays, List<String> results, Task<?> parentTask) {
        this.directory = directory;
        this.currentTime = currentTime;
        this.minDays = minDays;
        this.results = results;
        this.parentTask = parentTask;
    }

    @Override
    protected void compute() {
        if (isParentTaskCancelled()) {
            return;
        }

        List<FolderScannerTask> subTasks = new ArrayList<>();

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (isParentTaskCancelled()) {
                    return;
                }

                if (file.isDirectory()) {
                    try {
                        long daysSinceCreation = getDaysSinceCreation(file, currentTime);
                        if (daysSinceCreation >= minDays) {
                            String message = "Папка: \"" + file.getAbsolutePath() + "\", дней с момента создания: " + daysSinceCreation + "\n";
                            synchronized (results) {
                                results.add(message);
                            }
                        }
                    } catch (IOException e) {
                        String message = "Не удалось получить информацию о папке: " + file.getAbsolutePath() + " - " + e.getMessage() + "\n";
                        synchronized (results) {
                            results.add(message);
                        }
                    }

                    FolderScannerTask subTask = new FolderScannerTask(file, currentTime, minDays, results, parentTask);
                    subTasks.add(subTask);
                }
            }

            // Запускаем подзадачи параллельно
            invokeAll(subTasks);
        } else {
            String message = "Не удалось получить доступ к директории: " + directory.getAbsolutePath() + "\n";
            synchronized (results) {
                results.add(message);
            }
        }
    }

    private long getDaysSinceCreation(File file, long currentTime) throws IOException {
        Path path = file.toPath();
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        long creationMillis = 0L;

        FileTime creationTime = attrs.creationTime();
        creationMillis = creationTime.toMillis();

        // Если время создания недоступно или равно 0, используем время последней модификации
        if (creationMillis == 0L || creationMillis == -1L || creationMillis == FileTime.fromMillis(0).toMillis()) {
            creationMillis = attrs.lastModifiedTime().toMillis();
        }

        return (currentTime - creationMillis) / (24 * 60 * 60 * 1000);
    }

    private boolean isParentTaskCancelled() {
        return parentTask.isCancelled();
    }
}
