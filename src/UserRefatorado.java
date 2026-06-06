package login;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serviço de autenticação de usuários via banco de dados.
 *
 * <p>Esta versão corrige todos os problemas identificados na análise estática:
 * <ul>
 *   <li>SQL Injection eliminado via PreparedStatement</li>
 *   <li>Credenciais externalizadas via variáveis de ambiente</li>
 *   <li>Exceções registradas em log (não mais silenciadas)</li>
 *   <li>Recursos fechados automaticamente via try-with-resources</li>
 *   <li>Verificação de null antes do uso da conexão</li>
 *   <li>Estado removido dos campos de instância</li>
 *   <li>Retorno tipado com Optional para representar ausência de forma explícita</li>
 * </ul>
 *
 * <p><b>Nota de segurança:</b> Em produção, senhas devem ser armazenadas
 * com hash (BCrypt/Argon2). Este exemplo ilustra a estrutura correta, mas
 * a comparação de hash não está implementada para manter o foco na demonstração.
 */
public class UserRefatorado {

    private static final Logger LOGGER = Logger.getLogger(UserRefatorado.class.getName());

    // MELHORIA: credenciais lidas de variáveis de ambiente, não hardcoded
    private static final String DB_URL  = System.getenv("DB_URL");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASS = System.getenv("DB_PASSWORD");

    /**
     * Estabelece conexão com o banco de dados.
     *
     * <p>As credenciais são lidas de variáveis de ambiente para evitar
     * exposição no código-fonte ou em repositórios de controle de versão.
     *
     * @return Connection ativa
     * @throws SQLException se a conexão não puder ser estabelecida
     */
    private Connection conectarBD() throws SQLException {
        // MELHORIA: Class.forName() removido — obsoleto desde JDBC 4.0
        // MELHORIA: exceção declarada no throws, não silenciada
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    /**
     * Verifica as credenciais do usuário contra o banco de dados.
     *
     * <p>Retorna um {@link Optional} contendo o nome do usuário autenticado,
     * ou {@link Optional#empty()} se as credenciais forem inválidas.
     * Nunca retorna {@code null}.
     *
     * @param login login do usuário (não pode ser null)
     * @param senha senha do usuário em plain text (não pode ser null)
     * @return Optional com o nome do usuário, ou empty se não autenticado
     */
    public Optional<String> verificarUsuario(String login, String senha) {

        // MELHORIA: validação explícita de entrada
        if (login == null || login.isBlank() || senha == null || senha.isBlank()) {
            LOGGER.warning("Tentativa de autenticação com credenciais nulas ou vazias.");
            return Optional.empty();
        }

        // MELHORIA: query parametrizada — elimina SQL Injection
        final String sql = "SELECT nome FROM usuarios WHERE login = ? AND senha = ?";

        // MELHORIA: try-with-resources garante fechamento automático de todos os recursos
        try (Connection conn = conectarBD();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // MELHORIA: parâmetros definidos via setString — nunca concatenados
            ps.setString(1, login);
            ps.setString(2, senha);
            // NOTA DE PRODUÇÃO: senha deveria ser comparada via hash (BCrypt.checkpw)

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // MELHORIA: retorno direto, sem efeito colateral em campos de instância
                    return Optional.of(rs.getString("nome"));
                }
            }

        } catch (SQLException e) {
            // MELHORIA: exceção registrada em log — não mais silenciada
            LOGGER.log(Level.SEVERE, "Erro ao verificar usuário: " + e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * Exemplo de uso — demonstração do retorno tipado com Optional.
     */
    public static void main(String[] args) {
        UserRefatorado service = new UserRefatorado();

        Optional<String> resultado = service.verificarUsuario("lopes", "123");

        if (resultado.isPresent()) {
            System.out.println("Usuário autenticado: " + resultado.get());
        } else {
            System.out.println("Credenciais inválidas ou erro de conexão.");
        }
    }
}
