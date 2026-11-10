-- COPYのCSV形式で名前の改行・引用符・NULLと、EWKBの座標・SRIDを保持する。
COPY (
    SELECT id, object_type, name, encode(ST_AsEWKB(geom), 'hex') AS geom
    FROM game_object
    WHERE object_type = 'shrine'
    ORDER BY id
) TO STDOUT WITH (FORMAT csv, HEADER true, ENCODING 'UTF8');
