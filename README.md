# DoCard Android Reader

AndroidスマートフォンのNFC機能を使って、旭川のバスカード**DoCard**の残高を読み取るシンプルなアプリである。

DoCardはFeliCaカードではあるが、Suicaなどの交通系ICカードとは異なる独自システムである。  
そのため一般的なICカード残高確認アプリでは読み取ることができない。

本アプリではFeliCaの `Read Without Encryption`を直接使用し、DoCardのサービスコードを指定して残高を取得している。

## Blog

このアプリの背景などについては、以下の記事にまとめている。

https://mimoex.net/blog/docard-android-reader/

## Features

- Android NFC (`NfcF`) を使用
- DoCard System Code `0x8DB6`
- 履歴サービス `0x010F`（解析用）

カードをスマートフォンにかざすだけで、次の情報を表示する。

- IDm
- 最終利用日（推定）
- 残高

## Requirements

- Android device with NFC
- Android 8.0+

## Build

```bash
./gradlew assembleDebug
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Disclaimer

このアプリは研究・技術検証目的のものであり、
カードの改ざんや不正利用を目的としたものではない。
