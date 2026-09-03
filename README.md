# Templo da Glória Eterna — Android

Aplicativo Android oficial da Igreja Templo da Glória Eterna.

- Pacote: `br.com.templodagloriaeterna.app`
- Site conectado: https://www.templodagloriaeterna.com.br
- Instalação: APK assinado, distribuído diretamente pelo site
- Tecnologia: Trusted Web Activity (TWA)

## Segurança da assinatura

A chave de assinatura não fica neste repositório. A compilação usa quatro segredos protegidos:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Nunca publique a chave ou esses valores no código.
