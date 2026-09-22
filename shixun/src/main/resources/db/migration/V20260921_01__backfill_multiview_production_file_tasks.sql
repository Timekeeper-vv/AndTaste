-- Multiview projects approved before the production-file stages were added
-- were marked completed after project-manager review. Reopen only those
-- legacy projects and create the designer's missing production-file task.
INSERT INTO project_task (
    project_id,
    stage_index,
    stage_key,
    stage_name,
    assignee_role,
    status
)
SELECT
    p.id,
    2,
    'designer_production_files',
    '设计师制作生产文件',
    'designer',
    'pending'
FROM project p
WHERE p.category = 'multiview'
  AND p.status = 'completed'
  AND EXISTS (
      SELECT 1
      FROM project_task pm
      WHERE pm.project_id = p.id
        AND pm.stage_key = 'project_manager_review'
        AND pm.status = 'done'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM project_task production_file
      WHERE production_file.project_id = p.id
        AND production_file.stage_key = 'designer_production_files'
  );

UPDATE project p
SET p.status = 'active',
    p.current_stage_index = 2,
    p.current_stage_key = 'designer_production_files',
    p.current_stage_name = '设计师制作生产文件',
    p.current_assignee_role = 'designer',
    p.completed_at = NULL
WHERE p.category = 'multiview'
  AND p.status = 'completed'
  AND EXISTS (
      SELECT 1
      FROM project_task production_file
      WHERE production_file.project_id = p.id
        AND production_file.stage_key = 'designer_production_files'
        AND production_file.status = 'pending'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM project_task production_review
      WHERE production_review.project_id = p.id
        AND production_review.stage_key = 'production_review'
  );
