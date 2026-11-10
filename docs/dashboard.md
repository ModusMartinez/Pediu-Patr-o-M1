# Dashboard administrativo do Pediu Patrão

## Como executar

Com Java 21 e MongoDB em execução, inicie o projeto na raiz do repositório.

```sh
sh ./mvnw spring-boot:run '-Dspring-boot.run.jvmArguments=-Duser.timezone=America/Sao_Paulo'
```

Neste projeto, os horários dos pedidos são gravados como `LocalDateTime`. Execute a aplicação com o fuso acima para manter o mesmo dia comercial na gravação, na consulta e na exibição. Siga a configuração do MongoDB descrita no [README](../README.md).

Faça login em `/login` e abra **Dashboard** no menu ou acesse `/admin`. Use um perfil `ADMIN` ou `GERENTE`. Para gerar vendas de exemplo, registre pedidos pelo fluxo existente e finalize pelo menos um deles como `GERENTE`.

## Requisitos funcionais

| ID | Comportamento |
| --- | --- |
| RF01 | Exibir faturamento, quantidade de pedidos, pedidos finalizados e tíquete médio. |
| RF02 | Exibir faturamento em gráfico de linha e quantidade de pedidos em gráfico de barras, ambos por dia. |
| RF03 | Exibir a quantidade por status e até dez pedidos recentes com número, entrada, situação e total. |
| RF04 | Mostrar até cinco produtos ordenados pela quantidade de unidades vendidas. |
| RF05 | Filtrar todo o painel por datas e a tabela de pedidos recentes por status. |
| RF06 | Consumir a API de consulta do Spring Boot, integrada à coleção `pedidos` do MongoDB. |
| RF07 | Reconsultar ao abrir a tela, aplicar filtros ou clicar em **Atualizar**. Informar carregamento, período vazio, falha e horário da última consulta bem-sucedida. |

## Requisitos não funcionais

| ID | Comportamento |
| --- | --- |
| RNF01 | Restringir a tela e a API a `ADMIN` e `GERENTE`, com a sessão do Spring Security. |
| RNF02 | Retornar apenas os campos necessários ao painel. A API de pedidos recentes omite dados pessoais do cliente. |
| RNF03 | Usar o layout responsivo do sistema, com cartões e gráficos empilhados em telas estreitas e rolagem própria na tabela. Formatar datas e valores para o público brasileiro. |
| RNF04 | Aplicar o recorte por data no banco e limitar a consulta a 90 dias. Fazer os cálculos monetários no servidor com `BigDecimal`. |

## Regras de negócio

- A data de **entrada** define o dia de cada pedido nos filtros e nos gráficos. Os status exibidos refletem a situação atual desses pedidos.
- Faturamento é a soma do `total` registrado após descontos para pedidos finalizados. O tíquete médio divide esse valor pelo número de finalizados com total informado e arredonda para duas casas decimais. Quando esse grupo está vazio, o tíquete médio é zero.
- O ranking soma unidades de itens identificados pelo `produtoId` nos pedidos finalizados e exibe o nome histórico do produto. Em caso de empate, ordena pelo ID do produto.
- A leitura agrupa `ENTREGUE` em `FINALIZADO`, `EM_PREPARO` em `EM_PREPARACAO` e status ausentes ou desconhecidos em `OUTROS`. Pedidos antigos com data de entrada entram nas contagens mesmo com total ou itens ausentes. O ranking exige produto identificado e quantidade positiva.
- A série inclui todos os dias do intervalo, com zero nos dias vazios. Os pedidos recentes são ordenados da entrada mais recente para a mais antiga, com desempate pelo ID.

## API

Use o navegador autenticado no Pediu Patrão para abrir os exemplos abaixo. Um cliente HTTP também pode reutilizar o cookie da sessão obtido pelo login do sistema.

| Parâmetro | Rotas | Formato |
| --- | --- | --- |
| `inicio` e `fim` | Ambas | `AAAA-MM-DD`. Envie as duas datas ou omita ambas para usar os últimos 30 dias, incluindo hoje. As duas pontas são inclusivas. O máximo é 90 dias. |
| `status` | `/api/dashboard/pedidos` | Opcional. `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO` ou `OUTROS`. Omitir para ver todos. |

### `GET /api/dashboard/resumo`

```http
GET /api/dashboard/resumo?inicio=2026-11-10&fim=2026-11-10
```

Exemplo de resposta `200 OK` para o cenário de teste com quatro pedidos.

```json
{
  "inicio": "2026-11-10",
  "fim": "2026-11-10",
  "faturamento": 140.00,
  "totalPedidos": 4,
  "pedidosFinalizados": 2,
  "ticketMedio": 70.00,
  "serieDiaria": [{ "data": "2026-11-10", "pedidos": 4, "faturamento": 140.00 }],
  "porStatus": {
    "RECEBIDO": 1,
    "EM_PREPARACAO": 0,
    "PRONTO": 0,
    "SAIU_PARA_ENTREGA": 0,
    "RETIRADO": 0,
    "FINALIZADO": 2,
    "CANCELADO": 1,
    "OUTROS": 0
  },
  "ranking": [{ "produtoId": "pizza-01", "nome": "Pizza Margherita", "quantidade": 3 }]
}
```

### `GET /api/dashboard/pedidos`

```http
GET /api/dashboard/pedidos?inicio=2026-11-10&fim=2026-11-10&status=CANCELADO
```

Exemplo de resposta `200 OK`. Para um pedido antigo sem total, `total` vem como `null`. Se o filtro não encontrar pedidos, a resposta é `[]`.

```json
[
  {
    "id": "pedido-04",
    "entrada": "2026-11-10T11:53:20",
    "status": "CANCELADO",
    "total": 60.00
  }
]
```

### Erros

| HTTP | Causa |
| --- | --- |
| `400 Bad Request` | Data ausente ou inválida, intervalo invertido ou maior que 90 dias, ou status desconhecido. |
| `401 Unauthorized` | Requisição sem sessão autenticada. |
| `403 Forbidden` | Perfil sem permissão ou tentativa de alterar dados pelas rotas de consulta. |
| `503 Service Unavailable` | Falha temporária de conexão ou de tempo limite na consulta ao banco. |

Exemplo de `400` em `application/problem+json`.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Status inválido.",
  "instance": "/api/dashboard/pedidos"
}
```

## Testes e evidências

Para executar a suíte, use `sh ./mvnw test` ou, neste ambiente local, `sh ./scripts/maven.sh test`. Os testes de API usam o serviço real com o repositório simulado. A integração abaixo foi conferida separadamente com Spring Boot e MongoDB em execução em um banco temporário.

| Verificação | Resultado observado |
| --- | --- |
| Suíte Maven | 56 testes passaram, incluindo 7 casos novos para o dashboard. |
| Cálculos com MongoDB | Quatro pedidos, dois finalizados, um recebido e um cancelado. Faturamento de R$ 140,00, tíquete médio de R$ 70,00 e três pizzas no ranking. |
| Filtros | O filtro de cancelados mostrou um pedido na tabela. Um dia sem pedidos mostrou estado vazio e indicadores zerados. |
| Acesso e erros | A API retornou `401` sem sessão, `403` para `ATENDENTE` e `400` para uma data inválida. O teste automatizado também verificou `503` para indisponibilidade do banco. |
| Atualização | Após finalizar um pedido de R$ 40,00 e clicar em **Atualizar**, o faturamento subiu para R$ 180,00 e a contagem de finalizados passou de dois para três. |

[Indicadores e gráficos](evidencias/indicadores.jpg) · [Filtro por situação](evidencias/pedidos.jpg) · [Período vazio](evidencias/vazio.jpg) · [Painel atualizado](evidencias/atualizado.jpg) · [Vídeo de demonstração](dashboard-demo.mp4)
