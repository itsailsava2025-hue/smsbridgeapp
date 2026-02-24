SmsBridgeApp (ONE APK)
======================

Что это:
- Android приложение "SMS Bridge" (одно APK), которое привязывается к кабинету партнёра по QR (из CRM)
- После привязки оно в фоне:
  - забирает очередь исходящих SMS с сервера и отправляет через SIM
  - ловит входящие SMS и отправляет их в CRM

Как собрать APK (самый простой путь):
1) Откройте проект в Android Studio
2) Build -> Build Bundle(s) / APK(s) -> Build APK(s)
3) Возьмите app/build/outputs/apk/debug/app-debug.apk
4) Переименуйте в sms-bridge.apk и положите на сайт в /public_html/files/sms-bridge/sms-bridge.apk

Важно:
- На телефоне: разрешить "Установка из неизвестных источников" и выдать разрешения SMS.
- Для надёжности: отключить оптимизацию батареи для приложения SMS Bridge.
