package com.github.gaomond.topo.domain.port

import com.github.gaomond.topo.domain.model.CurrentArea
import java.util.UUID

interface LiveAreaQueryPort {
    /**
     * 指定ゲームのライブ座標から暫定凸包の面積と頂点列を求める。在室判定では絞り込まない。
     *
     * 緯度・経度が揃った点が 3 点未満、または計算結果が空なら null（未計測）。
     * 一直線・重複による退化は null とせず、測地面積 0 m² と線分または点の頂点列を返す。
     * 多角形の場合、[CurrentArea.hull] は順序付きの閉環となる。
     */
    fun currentLiveArea(gameId: UUID): CurrentArea?
}
