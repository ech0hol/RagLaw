-- Contract documents do not use knowledge-base category tree; disable CONTRACT nodes in UI.

UPDATE raglaw_category SET enabled = 0 WHERE doc_type = 'CONTRACT';
