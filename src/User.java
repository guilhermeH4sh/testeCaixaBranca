package login;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Classe User — implementação original reproduzida para fins de análise.
 *
 * PROBLEMAS IDENTIFICADOS (análise de caixa branca):
 * 1. Sem documentação Javadoc
 * 2. Credenciais hardcoded na URL de conexão
 * 3. Blocos catch vazios — exceções silenciadas
 * 4. SQL Injection por concatenação direta de strings
 * 5. Statement e ResultSet não fechados (vazamento de recursos)
 * 6. NullPointerException possível se conectarBD() retornar null
 * 7. Campos públicos mutáveis (result, nome) — estado compartilhado entre chamadas
 * 8. Senha comparada em plain text no banco de dados
 * 9. Class.forName() obsoleto desde JDBC 4.0
 */
public class User {

    /**
     * Estabelece conexão com o banco de dados MySQL.
     *
     * PROBLEMA: credenciais (user=lopes, password=123) estão hardcoded
     *           diretamente na URL — exposição de credenciais no código-fonte.
     *
     * PROBLEMA: bloco catch vazio — qualquer falha de conexão é silenciada.
     *           O método retorna null sem nenhuma indicação do motivo.
     *
     * PROBLEMA: Class.forName().newInstance() está obsoleto desde Java 6 /
     *           JDBC 4.0. O driver é registrado automaticamente.
     *
     * @return Connection ou null (em caso de falha silenciosa)
     */
    public Connection conectarBD() {
        Connection conn = null;
        try {
            // OBSOLETO: não é necessário desde JDBC 4.0
            Class.forName("com.mysql.Driver.Manager").newInstance();

            // VULNERABILIDADE: credenciais expostas no código-fonte
            String url = "jdbc:mysql://127.0.0.1/test?user=lopes&password=123";

            conn = DriverManager.getConnection(url);

        } catch (Exception e) {
            // FALHA CRÍTICA: exceção completamente ignorada
            // Se o banco estiver fora do ar, o método retorna null silenciosamente
        }
        return conn; // pode retornar null sem nenhum aviso
    }

    // PROBLEMA: campos públicos com estado mutável.
    // Se a mesma instância de User for reutilizada, `result` e `nome`
    // mantêm o estado da chamada anterior — comportamento não determinístico.
    public String nome = "";
    public boolean result = false;

    /**
     * Verifica se o login e senha fornecidos correspondem a um usuário cadastrado.
     *
     * VULNERABILIDADE CRÍTICA: SQL Injection.
     * A query é construída por concatenação direta de strings.
     * Um atacante pode injetar: login = "' OR '1'='1" para bypassar a autenticação.
     *
     * PROBLEMA: NullPointerException se conectarBD() retornar null.
     * Não há verificação de null antes de usar conn.
     *
     * PROBLEMA: Statement e ResultSet nunca são fechados.
     * Isso causa vazamento de recursos (connection pool leak em produção).
     *
     * PROBLEMA: senha comparada em plain text.
     * O banco armazena senhas sem hash — violação grave de segurança.
     *
     * @param login  login do usuário (não sanitizado)
     * @param senha  senha em plain text (não deve ser comparada diretamente)
     * @return true se autenticado, false caso contrário (sem distinguir erros)
     */
    public boolean verificarUsuario(String login, String senha) {

        String sql = "";

        // RISCO: se conectarBD() retornar null, a próxima linha lança NPE
        Connection conn = conectarBD();

        // INSTRUÇÃO SQL — construída por concatenação (SQL INJECTION)
        sql += "select nome from usuarios ";
        sql += "where login = " + "'" + login + "'";       // injection point
        sql += " and senha = " + "'" + senha + "';";        // injection point

        try {
            // Se conn == null, esta linha lança NullPointerException não tratada
            Statement st = conn.createStatement();

            // Executa query com possível SQL injection
            ResultSet rs = st.executeQuery(sql);

            // PROBLEMA: Statement e ResultSet nunca são fechados (sem finally / try-with-resources)

            if (rs.next()) {
                result = true;               // modifica estado da instância
                nome = rs.getString("nome"); // modifica estado da instância
            }

        } catch (Exception e) {
            // FALHA CRÍTICA: exceção silenciada
            // Impossível distinguir: credencial errada vs banco fora do ar vs NPE
        }

        return result; // retorna false tanto para "usuário inexistente" quanto para "erro"
    }

} // fim da class
