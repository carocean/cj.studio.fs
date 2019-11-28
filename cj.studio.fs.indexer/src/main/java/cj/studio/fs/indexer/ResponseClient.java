package cj.studio.fs.indexer;

public class ResponseClient {
    int state;
    String message;
    String dataText;

    public ResponseClient() {
    }

    public ResponseClient(int state, String message, String dataText) {
        this.state = state;
        this.message = message;
        this.dataText = dataText;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDataText() {
        return dataText;
    }

    public void setDataText(String dataText) {
        this.dataText = dataText;
    }
}
