package me.abdoabk.reportx.util;

public class DatabaseConfig {
    public enum Type { H2, MYSQL }

    private Type type;
    private H2Config h2;
    private MySQLConfig mysql;

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public H2Config getH2() { return h2; }
    public void setH2(H2Config h2) { this.h2 = h2; }

    public MySQLConfig getMysql() { return mysql; }
    public void setMysql(MySQLConfig mysql) { this.mysql = mysql; }

    public static class H2Config {
        private String path = "plugins/ReportX/data";
        private String username = "ReportX";
        private String password = "ReportX";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class MySQLConfig {
        private String host;
        private int port = 3306;
        private String database;
        private String username;
        private String password;
        private int poolSize = 10;
        private boolean useSsl = false;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getPoolSize() { return poolSize; }
        public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
        public boolean isUseSsl() { return useSsl; }
        public void setUseSsl(boolean useSsl) { this.useSsl = useSsl; }
    }
}