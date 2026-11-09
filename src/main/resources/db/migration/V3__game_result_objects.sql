-- 獲得時点の明細を元データの更新から独立させ、完了結果と同じ行に保存する。
ALTER TABLE game ADD COLUMN result_objects JSONB;
