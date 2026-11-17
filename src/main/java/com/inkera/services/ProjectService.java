package com.inkera.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.inkera.core.config.ConfigService;
import com.inkera.core.models.ProjectModel;
import com.inkera.util.AppLogger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProjectService {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataPath;

    public ProjectService() {
        this.dataPath = ConfigService.getInstance().getProjectsDataDir();
    }

    public List<ProjectModel> loadAllProjects() {
        List<ProjectModel> projects = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataPath, "*.json")) {
            for (Path entry : stream) {
                try (Reader reader = Files.newBufferedReader(entry)) {
                    ProjectModel model = gson.fromJson(reader, ProjectModel.class);
                    if (model != null) {
                        projects.add(model);
                    }
                }
            }
            AppLogger.debug("Manga projeleri yüklendi: Toplam " + projects.size() + " kayıt (" + dataPath + ")");
        } catch (IOException e) {
            AppLogger.error("Projeler yüklenirken hata oluştu: " + dataPath, e);
        }
        return projects;
    }

    public void saveProject(ProjectModel project) {
        // 1. Ana Merkeze (ConfigService -> user-data/projects) Kaydet
        Path hubFilePath = dataPath.resolve(project.getProjectId() + ".json");
        try (Writer writer = Files.newBufferedWriter(hubFilePath)) {
            gson.toJson(project, writer);
            AppLogger.debug("Proje merkezi kaydı güncellendi: " + hubFilePath);
        } catch (IOException e) {
            AppLogger.error("Proje merkezi kaydı yazılamadı: " + hubFilePath, e);
        }

        // 2. Projenin yerel klasöründeki (.inkera/meta.json) dosyayı da güncelle
        if (project.getProjectPath() != null) {
            Path localMetaFile = Paths.get(project.getProjectPath(), ".inkera", "meta.json");
            if (Files.exists(localMetaFile.getParent())) {
                try (Writer writer = Files.newBufferedWriter(localMetaFile)) {
                    gson.toJson(project, writer);
                    AppLogger.debug("Proje yerel meta.json güncellendi: " + localMetaFile);
                } catch (IOException e) {
                    AppLogger.error("Yerel meta.json güncellenemedi: " + localMetaFile, e);
                }
            }
        }
    }

    public void createMangaProjectScaffold(String projectName, String author, File selectedDirectory) {
        try {
            Path projectRoot = Paths.get(selectedDirectory.getAbsolutePath(), projectName);
            Files.createDirectories(projectRoot);

            Path inkeraFolder = projectRoot.resolve(".inkera");
            Path episodesFolder = projectRoot.resolve("Episodes");
            Files.createDirectories(inkeraFolder);
            Files.createDirectories(episodesFolder);

            ProjectModel newProject = new ProjectModel();
            newProject.setProjectId(UUID.randomUUID().toString());
            newProject.setTitle(projectName);
            newProject.setAuthor(author);
            newProject.setStatus("ONGOING");
            newProject.setTags(new ArrayList<>());
            newProject.setChapters(new ArrayList<>());
            newProject.setLastEdited(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            newProject.setProjectPath(projectRoot.toString());

            Path localMetaFile = inkeraFolder.resolve("meta.json");
            try (Writer writer = Files.newBufferedWriter(localMetaFile)) {
                gson.toJson(newProject, writer);
            }

            saveProject(newProject);

            AppLogger.info("Manga proje klasörleri başarıyla oluşturuldu: " + projectRoot);

        } catch (Exception e) {
            AppLogger.error("Proje klasörleri oluşturulamadı!", e);
        }
    }
}
