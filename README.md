# ScombZ Widgets

**ScombZ Widgets** は、芝浦工業大学の学習管理システム [ScombZ](https://scombz.shibaura-it.ac.jp/) の情報をAndroidのホーム画面ウィジェットで確認できるアプリです。

時間割・課題・バス時刻を一目で把握できます。

---

## 機能

### ウィジェット

| ウィジェット | 内容 |
|------------|------|
| 📅 **時間割** | 今日の授業を時限順に表示。現在進行中・次の授業をハイライト表示 |
| 📋 **課題** | 未提出課題を期限の近い順に表示。期限に応じた色分けで緊急度を視覚化 |
| 🚌 **バス時刻** | 大宮キャンパス周辺の次発バス（駅→学校・学校→駅）を表示 |

### アプリ画面

- **時間割タブ**: 曜日・時限ごとに授業を一覧表示
- **課題タブ**: ScombZ課題 + エクストラ課題をマージして表示
- **エクストラ課題タブ**: ScombZ外での課題提出をサポートする手動登録機能

### エクストラ課題

ScombZに登録されていない外部課題を手動でスケジュール登録できます。  
毎週指定した曜日・時刻になると**自動で課題として表示**され、期限を過ぎると自動で消えます。


### 自動更新

| 種類 | 頻度 | 内容 |
|------|------|------|
| 表示更新 | 1分ごと | 残り時間・次のバス・現在授業など |
| データ同期 | 1時間ごと | ScombZサーバーから時間割・課題を取得 |
| バスデータ | 15分ごと | バス時刻表サーバーから取得 |

---

## 動作環境

- **Android 12 以降** (API 31+)
- ScombZ のアカウント（芝浦工業大学の学生・教職員）

---

## セットアップ

1. APKをインストール
2. アプリを起動し、「ログイン」からScombZにサインイン
3. ホーム画面のウィジェット追加から好きなウィジェットを配置

> **注意**: Android 12+ では、正確なアラームの使用許可を求める場合があります。ウィジェットの1分更新を有効にするため、許可してください。

---

## 技術スタック

| カテゴリ | ライブラリ |
|---------|-----------|
| UI | Jetpack Compose, Material3 |
| ウィジェット | Glance AppWidget 1.1.1 |
| バックグラウンド処理 | WorkManager 2.10.0 |
| 時刻管理 | AlarmManager (`setExactAndAllowWhileIdle`) |
| 永続化 | DataStore Preferences, SharedPreferences |
| ネットワーク | OkHttp 4.12.0 |
| HTML解析 | JSoup 1.18.1 |
| JSON解析 | Gson 2.11.0 |
| 非同期 | Kotlin Coroutines 1.8.1 |

---

## アーキテクチャ

```
MainActivity (Compose UI / 3タブ)
│
├── ScombzRepository          ← ScombZサーバーとのやり取り、ローカルキャッシュ
├── ExtraAssignmentRepository ← エクストラ課題スケジュールの管理
├── BusRepository             ← バスデータのキャッシュ管理
│
├── DataSyncWorker (WorkManager) ← 1時間ごとのバックグラウンド同期
│
└── WidgetUpdateScheduler (AlarmManager) ← 1分ごとのUI再描画
    └── WidgetUpdateReceiver
        ├── TimetableWidget
        ├── AssignmentWidget  ← ScombZ課題 + エクストラ課題をマージ表示
        └── BusWidget
```

---

## ビルド

```bash
# デバッグビルド
./gradlew assembleDebug

# リリースビルド
./gradlew assembleRelease
```

---

## ライセンス

このアプリは芝浦工業大学の学生向けに作成された非公式ツールです。  
ScombZ は芝浦工業大学が提供するサービスです。本アプリとの公式な関係はありません。
