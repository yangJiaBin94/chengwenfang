-- Shared list of tables compared by verify_counts.py
-- Keep in sync with docs/cutover-pg-to-mysql.md mapping.

SELECT 'ai_provider_configs' AS table_name UNION ALL
SELECT 'jobs' UNION ALL
SELECT 'job_events' UNION ALL
SELECT 'workflow_runs' UNION ALL
SELECT 'workflow_node_runs' UNION ALL
SELECT 'model_calls' UNION ALL
SELECT 'skills' UNION ALL
SELECT 'skill_node_bindings' UNION ALL
SELECT 'workflow_definitions' UNION ALL
SELECT 'workflow_versions' UNION ALL
SELECT 'workflow_drafts' UNION ALL
SELECT 'workflow_version_skill_bindings' UNION ALL
SELECT 'courses' UNION ALL
SELECT 'content_documents' UNION ALL
SELECT 'content_document_versions' UNION ALL
SELECT 'content_theme_wizard_states' UNION ALL
SELECT 'generated_images' UNION ALL
SELECT 'source_channels' UNION ALL
SELECT 'source_fetch_runs' UNION ALL
SELECT 'source_items' UNION ALL
SELECT 'topic_clusters' UNION ALL
SELECT 'radar_batches' UNION ALL
SELECT 'topic_sources' UNION ALL
SELECT 'topic_briefs' UNION ALL
SELECT 'topic_evidence' UNION ALL
SELECT 'topic_research_runs' UNION ALL
SELECT 'topic_research_items' UNION ALL
SELECT 'topic_research_conflicts' UNION ALL
SELECT 'notifications' UNION ALL
SELECT 'human_reviews' UNION ALL
SELECT 'layout_themes' UNION ALL
SELECT 'layout_theme_versions' UNION ALL
SELECT 'video_parse_runs' UNION ALL
SELECT 'article_audit_runs' UNION ALL
SELECT 'article_audit_issues' UNION ALL
SELECT 'publishing_snapshots' UNION ALL
SELECT 'repair_runs' UNION ALL
SELECT 'article_layouts' UNION ALL
SELECT 'hot_article_repair_candidates' UNION ALL
SELECT 'workflow_templates' UNION ALL
SELECT 'workflow_alerts' UNION ALL
SELECT 'workflow_artifacts';
