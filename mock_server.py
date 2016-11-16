import time
from flask import Flask

app = Flask(__name__)


@app.route("/", methods=['GET'])
def callback():
    time.sleep(5)
    return 'OK'


if __name__ == "__main__":
    app.run(debug=True, port=5000)
