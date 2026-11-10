package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.CurrentArea
import java.util.UUID

interface CurrentAreaQueryPort {
    /**
     * 確定済みは保存済み確定位置、未確定は緯度・経度が揃ったライブ位置を1人1点採用する。
     * 在室判定では絞り込まず、ライブ未送信でも確定位置があれば採用する。
     * 確定座標と確定時刻の部分欠損は内部不整合とし、ライブ位置への代替や対象除外をしない。
     *
     * 採用人数・凸包・面積は同じ読み取りの点群から求める。同座標でも人数を減らさず、
     * 3人分未満は null（未計測）、3人分以上の退化は測地面積0 m²と点または線分の頂点列を返す。
     * 頂点は緯度・経度の順で、多角形の [CurrentArea.hull] は順序付きの閉環となる。
     */
    fun currentArea(gameId: UUID): CurrentArea?
}
