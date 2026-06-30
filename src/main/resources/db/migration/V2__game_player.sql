-- US-00: game / player スキーマ構築
-- game_object（GiST 含む）は V1 で投入済みのため対象外。
-- V1 は適用済みのため編集せず、本 V2 を新規追加する。

-- ゲームの状態を表す enum 型。
-- 状態値の追加はほぼ起きない前提のため enum 型を採用（D2 決定）。
CREATE TYPE game_status AS ENUM ('WAITING', 'ACTIVE', 'COMPLETED');

-- GAME（1ルーム。共有キー＝URL の gameId を兼ねる UUID 主キー）
CREATE TABLE game (
    id             UUID PRIMARY KEY,
    status         game_status NOT NULL,
    -- 部屋の定員（可変保持）。初回リリースは N=3 運用。上限 CHECK は張らない。
    player_count   INT NOT NULL,
    -- 集計対象種別（MVP は 'shrine'）
    object_type    TEXT NOT NULL,
    -- 面積閾値（m²。プリセットの実値）
    area_threshold DOUBLE PRECISION NOT NULL,
    -- 作成者の player.id。循環参照回避のため NULL 許容で作成し、後段 UPDATE で埋める。
    -- FK は player テーブル作成後に ALTER で後付けする（下部参照）。
    creator_player_id UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 結果カラム（COMPLETED まで NULL）
    polygon_geom   GEOMETRY(Polygon, 4326),
    area_sqm       DOUBLE PRECISION,
    area_valid     BOOLEAN,
    object_count   INT
);

-- PLAYER（ゲーム参加者。認証なし・UUID 識別）
CREATE TABLE player (
    id           UUID PRIMARY KEY,
    -- 所属ゲーム。ON DELETE 句は付けない（物理削除を前提にしない）。
    game_id      UUID NOT NULL REFERENCES game (id),
    display_name TEXT,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- ライブ位置（最新現在地・高頻度更新・友達ドット表示用）
    live_lat     DOUBLE PRECISION,
    live_lng     DOUBLE PRECISION,
    live_at      TIMESTAMPTZ,
    -- 確定座標（確定前 NULL）。US-13 で ST_ConvexHull にそのまま渡す。
    fixed_geom   GEOMETRY(Point, 4326),
    confirmed_at TIMESTAMPTZ
);

-- game.creator_player_id → player.id の FK を後付けし、game ↔ player の循環を解消する。
-- NULL 許容は維持（創成順序: game 作成 → creator player 作成 → creator_player_id UPDATE）。
ALTER TABLE game
    ADD CONSTRAINT fk_game_creator_player
    FOREIGN KEY (creator_player_id) REFERENCES player (id);

-- 所属ゲームでの player 検索（参加者一覧・ポーリング）を支える。
CREATE INDEX idx_player_game_id ON player (game_id);
