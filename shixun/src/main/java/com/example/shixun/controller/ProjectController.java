package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.ProjectService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Project workbench API: every product's lifecycle (design review -> PM
 * review -> production files -> quotation -> payment -> delivery) lives here
 * as a project with one active task per stage, instead of scattered menus.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    @Value("${creative.asset.private-root:${CREATIVE_ASSET_PRIVATE_ROOT:}}")
    private String creativePrivateAssetRoot;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/my-tasks")
    public List<Map<String, Object>> myTasks() {
        JwtService.Claims principal = authenticatedPrincipal();
        return projectService.getMyTasks(principal.role(), principal.username());
    }

    @GetMapping("/my-projects")
    public List<Map<String, Object>> myProjects() {
        JwtService.Claims principal = authenticatedPrincipal();
        return projectService.getMyProjects(principal.username());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return projectService.getProject(id);
    }

    @PostMapping("/tasks/{taskId}/complete")
    public Map<String, Object> completeTask(@PathVariable Long taskId, @RequestBody(required = false) TaskActionRequest req) {
        JwtService.Claims principal = authenticatedPrincipal();
        return projectService.completeTask(taskId, principal.username(), principal.role(), req == null ? null : req.comment);
    }

    @PostMapping("/tasks/{taskId}/reject")
    public Map<String, Object> rejectTask(@PathVariable Long taskId, @RequestBody(required = false) TaskActionRequest req) {
        JwtService.Claims principal = authenticatedPrincipal();
        if (req == null || blank(req.comment)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "驳回原因不能为空");
        }
        return projectService.rejectTask(taskId, principal.username(), principal.role(), req.comment);
    }

    @PostMapping("/tasks/{taskId}/production-review")
    public Map<String, Object> reviewProductionFile(@PathVariable Long taskId,
                                                     @RequestBody(required = false) ProductionReviewRequest req) {
        JwtService.Claims principal = authenticatedPrincipal();
        if (!"production".equals(principal.role()) && !"admin".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有生产人员可以审核生产文件");
        }
        if (req == null || !java.util.Set.of("approved", "rejected").contains(req.status)
                || ("rejected".equals(req.status) && blank(req.comment))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择通过或填写驳回原因");
        }
        return projectService.reviewProductionFile(taskId, req.status, principal.username(), principal.role(), req.comment);
    }

    @PostMapping(value = "/{projectId}/production-file", consumes = "multipart/form-data")
    public Map<String, Object> uploadProductionFile(@PathVariable Long projectId,
                                                     @RequestParam("file") MultipartFile file) throws Exception {
        JwtService.Claims principal = authenticatedPrincipal();
        if (!"designer".equals(principal.role()) && !"admin".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有设计师可以上传生产文件");
        }
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择生产文件压缩包");
        if (file.getSize() > 100L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生产文件压缩包不能超过100MB");
        String original = file.getOriginalFilename() == null ? "production-files.zip" : file.getOriginalFilename().replaceAll("[\\r\\n]", "").trim();
        if (!original.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生产文件必须是 ZIP 压缩包");
        }
        byte[] signature = new byte[4];
        try (java.io.InputStream in = file.getInputStream()) {
            if (in.read(signature) != 4 || signature[0] != 'P' || signature[1] != 'K') {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不是有效的 ZIP 压缩包");
            }
        }
        Path root = blank(creativePrivateAssetRoot)
                ? Path.of(System.getProperty("user.dir"), "data", "creative-assets")
                : Path.of(creativePrivateAssetRoot.trim());
        Path uploadDir = root.toAbsolutePath().normalize().resolve("uploads");
        Files.createDirectories(uploadDir);
        String storedName = "production-" + UUID.randomUUID() + ".zip";
        Files.copy(file.getInputStream(), uploadDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        Long assetId;
        String assetNo = "AST-PROD-" + UUID.randomUUID();
        try {
            projectService.getJdbc().update(
                    "INSERT INTO digital_asset (asset_no,title,asset_type,source_type,file_url,preview_url,status,format,tags,created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    assetNo, original, "project", "upload", "/uploads/" + storedName,
                    "/uploads/" + storedName, "review", "zip", "生产文件,项目上传", principal.userId());
            assetId = projectService.getJdbc().queryForObject("SELECT id FROM digital_asset WHERE asset_no=?", Long.class, assetNo);
        } catch (org.springframework.dao.DataAccessException e) {
            Files.deleteIfExists(uploadDir.resolve(storedName));
            throw e;
        }
        Map<String, Object> project = projectService.submitProductionFile(projectId, assetId, original,
                file.getSize(), file.getContentType(), principal.username(), principal.role());
        project.put("productionFileAssetId", assetId);
        return project;
    }

    @GetMapping("/{projectId}/assets")
    public List<Map<String, Object>> projectAssets(@PathVariable Long projectId) {
        String sql = "SELECT id, project_id projectId, asset_id assetId, asset_type assetType, file_name fileName, file_url fileUrl, file_size fileSize, mime_type mimeType, created_by createdBy, created_at createdAt FROM project_asset WHERE project_id=? ORDER BY created_at ASC";
        List<Map<String, Object>> assets = projectService.getJdbc().query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("projectId", rs.getLong("projectId"));
            row.put("assetId", rs.getLong("assetId"));
            row.put("assetType", rs.getString("assetType"));
            row.put("fileName", rs.getString("fileName"));
            row.put("fileUrl", rs.getString("fileUrl"));
            row.put("fileSize", rs.getLong("fileSize"));
            row.put("mimeType", rs.getString("mimeType"));
            row.put("createdBy", rs.getString("createdBy"));
            row.put("createdAt", rs.getTimestamp("createdAt"));
            return row;
        }, projectId);

        // Add signed URLs for each asset
        for (Map<String, Object> asset : assets) {
            Long assetId = (Long) asset.get("assetId");
            if (assetId != null) {
                asset.put("previewUrl", "/api/creative/ai/assets/" + assetId + "/preview-content");
                asset.put("downloadUrl", "/api/creative/ai/assets/" + assetId + "/content");
            }
        }

        return assets;
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    private JwtService.Claims authenticatedPrincipal() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null : attributes.getAttribute(
                JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof JwtService.Claims claims)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return claims;
    }

    public static class TaskActionRequest {
        public String comment;
    }

    public static class ProductionReviewRequest {
        public String status;
        public String comment;
    }
}
