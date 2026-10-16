# Pediu Patrão

Sistema de pizzaria desenvolvido para a Atividade Prática M1.
Usa Java, Spring Boot, Spring Security, Thymeleaf e MongoDB.

## Requisitos

- Java 21.
- MongoDB em execução.
- Internet no primeiro build para baixar as dependências.

O Maven Wrapper está incluído no projeto.

## Executar

Inicie o MongoDB em `127.0.0.1:27017`. O banco padrão é `pizzaria`.
Na pasta do projeto, execute:

```sh
sh ./mvnw spring-boot:run
```

Acesse `http://127.0.0.1:8080/login`.

No primeiro acesso, use o usuário `admin` e a senha `teste123`.
Ele é criado quando ainda não existe no banco.
Pelo ADMIN, cadastre os usuários GERENTE e ATENDENTE.

Para usar outro banco, configure `MONGODB_URI` e `MONGODB_DATABASE`.
Também é possível usar o arquivo `application-local.properties.example`
como modelo para `application-local.properties`.

## Perfis

- ADMIN: administra usuários e consulta pedidos, clientes e auditoria.
- GERENTE: gerencia pedidos, aplica descontos, cancela e consulta clientes e auditoria.
- ATENDENTE: cadastra e atualiza clientes, registra e acompanha pedidos.

Somente o GERENTE pode aplicar descontos e cancelar pedidos.
As permissões são verificadas no servidor. Formulários usam CSRF.

## Alterações da atividade

- RF01: três perfis e proteção das telas e APIs.
- RF02: pedidos com itens, quantidades, preços e total calculado no servidor.
- RF03: sequência de status e registro de entrada e saída com responsáveis.
- RF04: desconto de até 20% e cancelamento com justificativa antes da saída.
- RF05: auditoria persistente com usuário, perfil, horário e valores anteriores e novos.

Pedidos cancelados não permitem alterações.
A auditoria permite consulta apenas para ADMIN e GERENTE.
Os cadastros de clientes e produtos continuam disponíveis.

## Testes

```sh
sh ./mvnw test
```

Os testes usam serviços e repositórios simulados e não precisam do MongoDB.
