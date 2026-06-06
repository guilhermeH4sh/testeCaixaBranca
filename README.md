# Análise de Caixa Branca — Classe `User` (Java)

> Atividade de Teste de Caixa Branca | Revisão Estática, Grafo de Fluxo e Complexidade Ciclomática

---

## 1. Introdução

Este relatório documenta a análise estrutural completa de um código-fonte Java que implementa autenticação de usuários com banco de dados. A classe `User` expõe dois métodos principais: `conectarBD()`, responsável por estabelecer a conexão com o banco via JDBC, e `verificarUsuario(String login, String senha)`, que executa uma consulta SQL para autenticar credenciais.

A análise aplica técnicas de **Teste de Caixa Branca**, cobrindo inspeção estática, modelagem do grafo de fluxo, cálculo de complexidade ciclomática, identificação de caminhos básicos e revisão com melhorias implementadas.

---

## 2. Análise Estática do Código

### 2.1 Documentação

O código não possui nenhum comentário Javadoc ou inline. Nenhum método está documentado com `@param`, `@return` ou `@throws`. Para um código de autenticação — componente crítico de segurança — a ausência total de documentação é uma falha grave de qualidade.

### 2.2 Nomenclatura

| Elemento | Avaliação |
|---|---|
| Classe `User` | Aceitável, mas ambígua — poderia ser `UserAuthenticator` |
| Método `conectarBD()` | Aceitável em PT-BR, mas mistura idiomas com variáveis em inglês |
| Método `verificarUsuario()` | Adequado |
| Variável `conn` (campo de instância) | Problema: declarada como campo `public`, deveria ser local |
| Variável `result` | Campo `public boolean` com estado global — má prática |
| Variável `nome` | Campo `public String` — expõe estado interno desnecessariamente |

Há inconsistência de idioma: classe e métodos em português, imports e API em inglês (padrão Java), o que não é problemático por si só, mas a mistura de `conn`, `st`, `rs` sem nomenclatura expressiva prejudica a legibilidade.

### 2.3 Legibilidade e Organização

O código apresenta problemas estruturais relevantes:

- A construção da query SQL é feita em três linhas separadas com `+=` sem clareza do resultado final.
- Os campos `result` e `nome` são públicos e mutáveis, acumulando estado entre chamadas — comportamento não determinístico se a mesma instância for reutilizada.
- O método `conectarBD()` retorna `null` silenciosamente em caso de falha (bloco `catch` vazio).
- `Statement` e `ResultSet` não são fechados em nenhum momento, causando vazamento de recursos.

### 2.4 Tratamento de Exceções

O tratamento de exceções é a falha mais crítica do código:

```java
// conectarBD():
} catch (Exception e) { }   // exceção completamente ignorada

// verificarUsuario():
} catch (Exception e) { }   // exceção completamente ignorada
```

Ambos os blocos `catch` estão **vazios**, o que significa que qualquer falha (timeout de conexão, query malformada, banco fora do ar) é silenciada. O código retorna `false` ou `null` sem nenhuma indicação do motivo, impossibilitando diagnóstico e tornando o sistema não confiável.

A exceção deveria ser pelo menos registrada em log, e idealmente relançada ou convertida em uma exceção de negócio.

### 2.5 Segurança — SQL Injection

Este é o problema mais grave do código. A query é construída por concatenação direta de strings:

```java
sql += "select nome from usuarios ";
sql += "where login = " + "'" + login + "'";
sql += " and senha = " + "'" + senha + "';";
```

Um atacante pode injetar `' OR '1'='1` no campo `login` e obter acesso irrestrito. A solução correta é usar `PreparedStatement`:

```java
String sql = "SELECT nome FROM usuarios WHERE login = ? AND senha = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setString(1, login);
ps.setString(2, senha);
```

Além disso, senhas nunca devem ser armazenadas em texto puro. O código compara `senha` diretamente com o banco, indicando que a senha está em plain text — violação grave das boas práticas de segurança (deveria usar hash com BCrypt ou similar).

### 2.6 Credenciais Hardcoded

```java
String url = "jdbc:mysql://127.0.0.1/test?user=lopes&password=123";
```

Usuário e senha do banco estão expostos diretamente no código-fonte. Qualquer pessoa com acesso ao repositório ou ao `.class` descompilado tem acesso às credenciais. Credenciais devem ser externalizadas via variáveis de ambiente ou arquivos de configuração protegidos.

### 2.7 Riscos de NullPointerException

Se `conectarBD()` falhar silenciosamente (bloco catch vazio), ela retorna `null`. Em `verificarUsuario()`, a linha seguinte é:

```java
Connection conn = conectarBD();
// ... sem verificação de null
Statement st = conn.createStatement(); // NPE aqui se conn == null
```

Não há nenhuma verificação `if (conn == null)` antes de usar a conexão, garantindo um `NullPointerException` não tratado em cenários de falha de conexão.

### 2.8 Recursos Não Fechados

`Statement st` e `ResultSet rs` são criados mas nunca fechados. O correto é usar try-with-resources do Java 7+:

```java
try (Connection conn = conectarBD();
     PreparedStatement ps = conn.prepareStatement(sql)) {
    // uso dos recursos — fechamento automático garantido
}
```

### 2.9 Redundâncias e Más Práticas

- `result` e `nome` como campos públicos de instância: estado compartilhado entre chamadas, risco de condição de corrida em ambientes concorrentes.
- `Class.forName("com.mysql.Driver.Manager").newInstance()` está obsoleto desde JDBC 4.0 (Java 6). O driver é carregado automaticamente pelo `DriverManager`.
- O método retorna um `boolean` mas também modifica `this.nome` — dois efeitos colaterais em um método que deveria ser uma função pura de autenticação.

---

## 3. Grafo de Fluxo

O grafo de fluxo representa o método `verificarUsuario()`, que concentra a lógica principal e as decisões de controle do código.

### Nós identificados

| Nó | Descrição |
|---|---|
| N1 | Início do método |
| N2 | Declarar `sql = ""`; chamar `conectarBD()` |
| N3 | Montar query SQL por concatenação |
| N4 | Decisão implícita: `conn` é null? (risco de NPE) |
| N5 | `try`: `conn.createStatement()` → `st.executeQuery(sql)` |
| N6 | Decisão: `rs.next()` retorna true ou false? |
| N7 | `result = true`; `nome = rs.getString("nome")` |
| N8 | `catch (Exception e)` — bloco vazio |
| N9 | `return result` |
| N10 | Fim |

### Arestas identificadas

| Aresta | De → Para | Condição |
|---|---|---|
| A1 | N1 → N2 | — |
| A2 | N2 → N3 | — |
| A3 | N3 → N4 | — |
| A4 | N4 → N5 | conn não é null |
| A5 | N4 → N9 | conn é null (NPE lançada, não capturada aqui) |
| A6 | N5 → N6 | query executada com sucesso |
| A7 | N5 → N8 | exceção lançada durante execução |
| A8 | N6 → N7 | `rs.next()` = true (registro encontrado) |
| A9 | N6 → N8 | `rs.next()` = false (nenhum registro) → vai ao return |
| A10 | N7 → N9 | — |
| A11 | N8 → N9 | — |

> O grafo gerado está disponível na imagem `grafo_fluxo.png` na raiz do repositório.

---

## 4. Complexidade Ciclomática

### Fórmula aplicada

```
V(G) = E − N + 2P
```

Onde:
- **E** = número de arestas
- **N** = número de nós
- **P** = número de componentes conectados (grafo único = 1)

### Valores obtidos

| Métrica | Valor |
|---|---|
| Arestas (E) | 11 |
| Nós (N) | 10 |
| Componentes (P) | 1 |

### Cálculo

```
V(G) = 11 − 10 + 2 × 1
V(G) = 11 − 10 + 2
V(G) = 3
```

### Interpretação

Complexidade ciclomática **V(G) = 3** indica que existem **3 caminhos linearmente independentes** no método. Isso também significa que são necessários pelo menos 3 casos de teste para cobrir todos os fluxos independentes do código.

Uma complexidade de 3 é considerada baixa (ideal ≤ 10), mas neste caso reflete a simplicidade estrutural do método — não a ausência de problemas de qualidade, que são graves apesar do fluxo simples.

---

## 5. Caminhos Básicos

Com V(G) = 3, existem **3 caminhos básicos independentes**:

### Caminho 1 — Autenticação bem-sucedida

```
N1 → N2 → N3 → N4 → N5 → N6 → N7 → N9 → N10
```

**Fluxo:** Conexão estabelecida com sucesso. Query executada. `rs.next()` retorna `true`. `result` é definido como `true` e `nome` é lido. Método retorna `true`.

**Caso de teste correspondente:** Login e senha corretos, banco disponível, usuário existe na tabela.

**Resultado esperado:** `verificarUsuario("lopes", "123")` → `true`; `nome` preenchido.

---

### Caminho 2 — Usuário não encontrado

```
N1 → N2 → N3 → N4 → N5 → N6 → N8 → N9 → N10
```

**Fluxo:** Conexão estabelecida. Query executada. `rs.next()` retorna `false` (nenhum registro corresponde às credenciais). Flui para o `catch` ou diretamente para o `return`. Método retorna `false`.

**Caso de teste correspondente:** Login ou senha incorretos, banco disponível, mas usuário não existe.

**Resultado esperado:** `verificarUsuario("usuario_invalido", "senha_errada")` → `false`.

---

### Caminho 3 — Exceção durante execução / conexão nula

```
N1 → N2 → N3 → N4 → N5 → N8 → N9 → N10
```

ou, via NPE:

```
N1 → N2 → N3 → N4(null) → N9 → N10
```

**Fluxo:** Banco indisponível (`conectarBD()` retorna `null`) ou exceção lançada durante `createStatement()` / `executeQuery()`. Bloco `catch` captura (ou NPE propaga). Método retorna `false`.

**Caso de teste correspondente:** Banco de dados fora do ar, driver não encontrado, timeout de conexão.

**Resultado esperado:** método retorna `false` sem nenhuma indicação de erro — comportamento silenciosamente incorreto.

---

## 6. Melhorias Implementadas

O código revisado está no arquivo `UserRefatorado.java` e corrige todos os problemas identificados:

### 6.1 SQL Injection → PreparedStatement

```java
// ANTES (vulnerável):
sql += "where login = " + "'" + login + "'";

// DEPOIS (seguro):
String sql = "SELECT nome FROM usuarios WHERE login = ? AND senha_hash = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setString(1, login);
ps.setString(2, hashSenha(senha));
```

### 6.2 Credenciais externalizadas

```java
// ANTES (hardcoded):
String url = "jdbc:mysql://127.0.0.1/test?user=lopes&password=123";

// DEPOIS (variável de ambiente):
String url = System.getenv("DB_URL");
String user = System.getenv("DB_USER");
String password = System.getenv("DB_PASSWORD");
```

### 6.3 Tratamento de exceções adequado

```java
// ANTES (silenciado):
} catch (Exception e) { }

// DEPOIS (registrado e relançado):
} catch (SQLException e) {
    logger.error("Erro ao autenticar usuário: {}", e.getMessage(), e);
    return false;
}
```

### 6.4 Try-with-resources (sem vazamento de recursos)

```java
try (Connection conn = conectarBD();
     PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, login);
    ps.setString(2, hashSenha(senha));
    try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
            return rs.getString("nome");
        }
    }
}
```

### 6.5 Verificação de null e eliminação de campos públicos

```java
// Campos `result` e `nome` removidos como atributos públicos de instância.
// Método retorna diretamente o valor, sem efeito colateral.
public Optional<String> verificarUsuario(String login, String senha) {
    if (login == null || senha == null) return Optional.empty();
    // ...
}
```

### 6.6 Hash de senha

```java
private String hashSenha(String senha) {
    // Uso de BCrypt — nunca comparar senha em plain text com banco
    return BCrypt.hashpw(senha, BCrypt.gensalt());
}
```

---

## 7. Conclusão

### Importância do teste estrutural

A análise de Caixa Branca revelou que um código aparentemente simples (apenas 34 linhas, complexidade ciclomática 3) pode conter múltiplas vulnerabilidades críticas: SQL injection, credenciais expostas, senhas em plain text, recursos não fechados e exceções silenciadas. Nenhum teste de caixa preta seria capaz de detectar alguns desses problemas sem inspecionar o código-fonte diretamente.

### Dificuldades encontradas

A principal dificuldade foi modelar corretamente o nó de decisão implícito referente ao `NullPointerException` — o código não verifica explicitamente se `conn` é null, então o desvio de fluxo ocorre de forma não intencional (por exceção em runtime, não por condicional estruturada). Isso evidencia a importância de tornar explícitas todas as decisões no código.

### Impacto da revisão de código

A versão revisada eliminou todas as vulnerabilidades de segurança identificadas e converteu o código para seguir boas práticas Java modernas (try-with-resources, PreparedStatement, variáveis de ambiente, logging). A refatoração reduziu drasticamente a superfície de ataque do componente.

### Importância da qualidade de software

Este exercício demonstra que qualidade de software não é apenas funcionalidade — é segurança, manutenibilidade e confiabilidade. Um código que "funciona" em ambiente controlado pode ser completamente inadequado para produção quando analisado estruturalmente. A revisão de código e o teste de caixa branca são práticas indispensáveis em qualquer pipeline de desenvolvimento profissional.

---

## Estrutura do repositório

```
/
├── README.md                    ← este relatório
├── src/
│   ├── User.java                ← código original reproduzido com comentários
│   └── UserRefatorado.java      ← versão revisada e corrigida
├── docs/
│   └── grafo_fluxo.png          ← imagem do grafo de fluxo
└── planilha/
    └── PLANO_DE_TESTE.xlsx      ← aba Caixa Branca (Estático) preenchida
```

---

*Análise realizada com base no código-fonte fornecido na atividade. Todos os problemas identificados foram documentados e corrigidos na versão revisada.*
