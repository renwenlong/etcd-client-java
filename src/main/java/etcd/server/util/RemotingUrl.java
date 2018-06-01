package etcd.server.util;

public class RemotingUrl {

    private String domain;
    private String host;
    private int port;
    private int connectionTimeout = 3000;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemotingUrl that = (RemotingUrl) o;
        if (port != that.port) return false;
        return !(domain != null ? !domain.equals(that.domain) : that.domain != null);

    }

    @Override
    public int hashCode() {
        int result = domain != null ? domain.hashCode() : 0;
        result = 31 * result + port;
        return result;
    }

    @Override
    public String toString() {
        return "RemotingUrl{" +
                "domain='" + domain + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", connectionTimeout=" + connectionTimeout +
                '}';
    }
}
