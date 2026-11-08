# コンポーネントの責務と境界の命名

業務ルール、操作の手順、HTTP や DB の表現が同じ場所に混ざると、変更時にどのコードを直すべきか判断しづらくなる。本書では、実装時の判断を揃えるために、各コンポーネントの責務と、境界を通る型の名前を定める。

例は駐車場の入庫操作で統一する。型名は今後の実装に使う命名例とする。

## 1. 入力から結果までの流れ

```text
HTTP の入庫要求
  → Controller が外部入力を検証し、内部表現に変換
  → UseCase が Repository から ParkingLot を取得
  → ParkingLot が入庫可否を判断し、区画と駐車券の状態を変更
  → UseCase が Repository に保存を依頼
  → Controller が操作結果を HTTP の応答に変換
```

## 2. コンポーネントの責務

| コンポーネント | 責務 | 自身が発出する例外 | 駐車場での処理例 |
| --- | --- | --- | --- |
| Controller／Web Adapter | 外部入力の形式・必須項目・型を検証し、内部表現へ変換する。UseCase の結果・例外を HTTP の表現へ変換する。 | 入力形式の不備を表す例外。 | 文字列の車種を `CarType` に変換し、入庫操作の入力を作る。 |
| UseCase／Application Service | 必要なドメインモデルを取得し、ドメイン操作を呼び、保存する。処理順序とトランザクションを管理する。 | 操作の前提が満たされないことを表すアプリケーション例外。例として `ParkingLotNotFoundException`。 | 駐車場を取得 → `lot.enter(carType)` → 区画と駐車券を同じトランザクションで保存する。 |
| Domain | 業務上の不変条件、操作の可否、状態遷移、計算規則を表す。 | 業務ルール違反を表すドメイン例外。`ParkingException.Full`・`ParkingTicketException.Billed`・`ParkingTicketException.Invalid` など。 | 対応車種の空き区画を選び、使用中に変更して駐車券を発行する。 |
| Repository／永続化 Adapter | 永続化データからドメインモデルを復元して返す。保存時はドメインモデルを永続化表現に変換して保存する。 | 保存競合を表す例外。例として `SlotUpdateConflictException`。 | `ParkingLotRecord` などから `ParkingLot` を復元する。変更された区画と発行した駐車券を保存する。 |

## 3. 経路ごとの型名

| 経路 | 命名 | Python のコード例 |
| --- | --- | --- |
| HTTP → Controller | `XxxRequest` | `request = EnterParkingLotRequest(**json.loads(body))` |
| Controller → UseCase | `XxxInput` | `input_data = EnterParkingLotInput(lot_id, CarType[request.car_type])` |
| DB／ORM → Repository 内部 | `XxxRecord` | `record = session.get(ParkingLotRecord, lot_id)` |
| Repository → UseCase | ドメインモデル | `return record.to_domain()` → `ParkingLot` |
| UseCase → Controller | `XxxResult` | `return EnterParkingLotResult(ticket.id, ticket.slot_id)` |
| Controller → HTTP | `XxxResponse` | `return json.dumps(asdict(EnterParkingLotResponse.from_result(result)))` |
