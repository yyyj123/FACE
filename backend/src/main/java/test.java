import java.sql.*;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

public class test {

    /**
     * 测试数据库连接
     * @param url      数据库连接URL
     * @param username 用户名
     * @param password 密码
     * @return 连接是否成功
     */
    public static TestResult testConnection(String url, String username, String password) {
        Connection connection = null;
        TestResult result = new TestResult();

        try {
            // 1. 加载驱动（MySQL 5.x和8.x有差异）
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                result.addMessage("✓ 驱动加载成功 (MySQL 8.x+)");
            } catch (ClassNotFoundException e) {
                // 尝试旧版驱动
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                    result.addMessage("✓ 旧版驱动加载成功 (MySQL 5.x)");
                } catch (ClassNotFoundException e2) {
                    result.setSuccess(false);
                    result.addMessage("✗ 驱动加载失败: " + e2.getMessage());
                    result.addMessage("  请检查MySQL驱动包(mysql-connector-j)是否已添加到项目中");
                    return result;
                }
            }

            // 2. 建立连接
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("useSSL", "false");
            props.setProperty("serverTimezone", "UTC");
            props.setProperty("connectTimeout", "5000"); // 5秒超时

            result.addMessage("尝试连接到: " + maskPassword(url));

            connection = DriverManager.getConnection(url, props);
            result.addMessage("✓ 连接建立成功");

            // 3. 获取数据库信息
            DatabaseMetaData metaData = connection.getMetaData();
            result.addMessage("数据库产品: " + metaData.getDatabaseProductName());
            result.addMessage("数据库版本: " + metaData.getDatabaseProductVersion());
            result.addMessage("驱动版本: " + metaData.getDriverVersion());

            // 4. 测试简单查询
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT 1");
            if (rs.next()) {
                result.addMessage("✓ 查询测试通过");
            }
            rs.close();
            stmt.close();

            // 5. 测试可用数据库
            testDatabaseOperations(connection, result);

            result.setSuccess(true);

        } catch (SQLException e) {
            result.setSuccess(false);
            handleSQLException(e, result);
        } finally {
            // 关闭连接
            if (connection != null) {
                try {
                    connection.close();
                    result.addMessage("✓ 连接正常关闭");
                } catch (SQLException e) {
                    result.addMessage("⚠ 连接关闭异常: " + e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * 测试数据库基本操作
     */
    private static void testDatabaseOperations(Connection conn, TestResult result) throws SQLException {
        try {
            // 获取所有数据库
            ResultSet rs = conn.getMetaData().getCatalogs();
            int dbCount = 0;
            StringBuilder databases = new StringBuilder("可用数据库: ");
            while (rs.next()) {
                if (dbCount > 0) databases.append(", ");
                databases.append(rs.getString(1));
                dbCount++;
            }
            rs.close();

            if (dbCount > 0) {
                result.addMessage("✓ " + databases.toString());
            } else {
                result.addMessage("⚠ 未找到可用数据库");
            }

        } catch (SQLException e) {
            result.addMessage("⚠ 获取数据库列表失败: " + e.getMessage());
        }
    }

    /**
     * 处理SQL异常，提供友好的错误信息
     */
    private static void handleSQLException(SQLException e, TestResult result) {
        int errorCode = e.getErrorCode();
        String state = e.getSQLState();

        result.addMessage("✗ 连接失败 - 错误代码: " + errorCode);
        result.addMessage("  SQL状态: " + state);
        result.addMessage("  错误信息: " + e.getMessage());

        // 根据错误码提供解决方案
        switch (errorCode) {
            case 0: // 通常表示网络问题
                if (e.getMessage().contains("Communications link failure")) {
                    result.addMessage("  ▶ 可能原因: MySQL服务未启动 或 网络不可达");
                    result.addMessage("  ▶ 解决方案: 检查MySQL服务状态，确认端口(如3306)是否开放");
                }
                break;
            case 1045: // 访问被拒绝
                result.addMessage("  ▶ 可能原因: 用户名或密码错误");
                result.addMessage("  ▶ 解决方案: 检查你的 username 和 password 设置");
                break;
            case 1049: // 未知数据库
                result.addMessage("  ▶ 可能原因: URL中指定的数据库不存在");
                result.addMessage("  ▶ 解决方案: 检查URL中的数据库名称，或先连接默认库创建它");
                break;
            case 2003: // 无法连接到服务器
                result.addMessage("  ▶ 可能原因: MySQL服务未运行 或 被防火墙拦截");
                result.addMessage("  ▶ 解决方案: 启动MySQL服务，检查防火墙和IP白名单");
                break;
            case 1130: // 主机不允许连接
                result.addMessage("  ▶ 可能原因: 该账号没有远程访问权限");
                result.addMessage("  ▶ 解决方案: 在数据库中执行 GRANT ALL PRIVILEGES ON *.* TO '用户名'@'%'");
                break;
        }
    }

    /**
     * 隐藏URL中的密码(如果有)
     */
    private static String maskPassword(String url) {
        return url.replaceAll("password=[^&]*", "password=****");
    }

    /**
     * 程序入口
     */
    public static void main(String[] args) {
        // =========================================================
        // 👇 请在这里修改为你的真实数据库信息 👇
        // =========================================================
        String url = "jdbc:mysql://127.0.0.1:3307/car?allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf-8&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT%2B8&useSSL=false";
        String username = "root";
        String password = "123456"; // 换成你的密码
        // =========================================================

        // 如果通过命令行传入参数，则覆盖默认配置
        if (args.length >= 3) {
            url = args[0];
            username = args[1];
            password = args[2];
        }

        System.out.println("=== MySQL数据库连接测试 ===");
        System.out.println("测试时间: " + new java.util.Date());
        System.out.println("连接 URL: " + url);
        System.out.println("用 户 名: " + username);
        System.out.println();

        // 执行测试
        TestResult result = testConnection(url, username, password);

        // 打印结果
        System.out.println("=== 测试详情 ===");
        for (String msg : result.getMessages()) {
            System.out.println(msg);
        }

        if (result.isSuccess()) {
            System.out.println("\n✅ 结论: 数据库连接测试成功！");
        } else {
            System.out.println("\n❌ 结论: 数据库连接测试失败，请参考上方提示排查。");
        }
    }
}

/**
 * 测试结果封装类
 */
class TestResult {
    private boolean success = false;
    private List<String> messages = new ArrayList<>();

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public List<String> getMessages() { return messages; }
    public void addMessage(String message) { messages.add(message); }
}