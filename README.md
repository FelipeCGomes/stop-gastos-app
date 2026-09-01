# Stop Gastos — Android nativo

Versão Android nativa do **Stop Gastos**, migrada do projeto web `FelipeCGomes/top-contole`.

## Stack

- Kotlin como linguagem principal do app
- Java para regras/cálculos financeiros compartilhados
- Jetpack Compose + Material 3
- MVVM
- Firebase Authentication
- Cloud Firestore
- Firebase Storage
- Android Credential Manager para login Google
- Sem WebView
- Sem HTML/CSS/JavaScript na interface Android

## Compatibilidade com o site

O aplicativo preserva a estrutura modular já usada pelo Stop Gastos web:

```
users/{uid}/data/transactions
users/{uid}/data/recurring
users/{uid}/data/incomeSources
users/{uid}/data/cards
```

O conteúdo continua no campo `value`, permitindo que o Android leia os mesmos dados gravados pelo site.

A camada nativa preserva campos desconhecidos ao atualizar registros existentes, reduzindo o risco de o app Android apagar metadados criados pela versão web.

## Funcionalidades nativas já implementadas

- Login Google preparado com Credential Manager
- Dashboard mensal
- Receitas e despesas
- Lançamentos
- Custos fixos
- Cartões de crédito
- Vale-refeição
- Vale-alimentação
- Vale-combustível
- Outro benefício
- Parcelamento de cartão em até 60x
- Divisão das parcelas em centavos sem diferença de arredondamento
- Fechamento e vencimento do cartão
- Projeção das parcelas para os meses/faturas seguintes
- Custo fixo parcelado como série única
- Exclusão de compra parcelada removendo o grupo completo
- Firestore em tempo real
- Indicador de sincronização
- Exclusão de custos fixos e cartões
- Tema Material 3
- CI para compilar o APK debug

## Firebase

O projeto web usa:

- Firebase project: `stopgastos`
- Project ID: `stopgastos`

A inicialização do Firebase está configurada nativamente em `StopGastosApplication.kt` usando a configuração pública já existente no projeto web.

### Passo obrigatório para o login Google no Android

O Firebase precisa conhecer o aplicativo Android e seu certificado.

1. No Firebase Console, abra o projeto `stopgastos`.
2. Adicione um app Android com package:
   `com.example.stop_fgastos`
3. Adicione o SHA-1 do certificado de debug/release.
4. Confirme que o provedor Google está habilitado no Firebase Authentication.
5. Copie o **Web Client ID / Server Client ID**.
6. Substitua em:
   `app/src/main/res/values/strings.xml`

```xml
<string name="default_web_client_id">SEU_WEB_CLIENT_ID.apps.googleusercontent.com</string>
```

O app detecta o placeholder e bloqueia o botão de login até essa configuração existir.

> Para publicação, o ideal é também baixar o `google-services.json` do app Android e migrar a inicialização para o Google Services Gradle Plugin. O arquivo real não deve ser inventado nem produzido manualmente.

## Parcelamento compatível com o web

Para cartão de crédito:

- compra até o dia de fechamento: entra na fatura do mês;
- compra depois do fechamento: entra na fatura do mês seguinte;
- compras parceladas usam o vencimento do cartão como data das parcelas futuras;
- `invoiceMonth`, `installmentGroup`, `installmentNo`, `installmentCount`, `purchaseTotal` e `purchaseDate` são mantidos;
- valores são divididos em centavos, distribuindo eventual resto entre as primeiras parcelas.

## Estrutura principal

```
app/src/main/java/com/example/stop_fgastos/
├── MainActivity.kt
├── StopGastosApplication.kt
├── auth/
│   └── GoogleAuthManager.kt
├── data/
│   └── FinanceRepository.kt
├── model/
│   └── FinanceModels.kt
├── ui/
│   └── StopGastosApp.kt
├── util/
│   └── FinanceCalculator.java
└── viewmodel/
    └── MainViewModel.kt
```

## Próximos módulos da migração do site

A arquitetura está preparada para receber os demais documentos que já existem no Firestore:

- Contas e carteiras
- Rendas recorrentes em Configurações
- Contas a pagar/receber
- Transferências
- Orçamentos
- Metas
- Categorias personalizadas
- Calendário financeiro
- Relatórios
- Lista de compras pessoal
- Lista de compras familiar
- Família, membros e convites
- Notificações
- Exclusão de conta e demais configurações

Esses módulos devem seguir o mesmo padrão `users/{uid}/data/{sectionId}` já utilizado pelo site.
