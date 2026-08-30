import json
import socket
import time



class GpsService:
    def status(self) -> dict:

        result = {
            "status": "unavailable",
            "latitude": None,
            "longitude": None,
            "altitude": None,
            "mode": 0,
            "time_utc": None,
        }

        try:
            with socket.create_connection(
                ("127.0.0.1", 2947),
                timeout=1.0,
            ) as gps_socket:
                gps_socket.settimeout(0.25)
                gps_socket.sendall(
                    b'?WATCH={"enable":true,"json":true};\n'
                )

                deadline = time.monotonic() + 1.5
                buffer = ""

                while time.monotonic() < deadline:
                    try:
                        data = gps_socket.recv(4096)
                    except socket.timeout:
                        continue

                    if not data:
                        break

                    buffer += data.decode(
                        "utf-8",
                        errors="replace",
                    )

                    while "\n" in buffer:
                        line, buffer = buffer.split("\n", 1)

                        try:
                            message = json.loads(line)
                        except json.JSONDecodeError:
                            continue

                        if message.get("class") != "TPV":
                            continue

                        mode = int(message.get("mode", 0) or 0)
                        result["mode"] = mode

                        if mode == 1:
                            result["status"] = "no_fix"

                        if mode >= 2:
                            result["status"] = "fix"
                            result["latitude"] = message.get("lat")
                            result["longitude"] = message.get("lon")

                            altitude = message.get("altHAE")
                            if altitude is None:
                                altitude = message.get("altMSL")

                            result["altitude"] = altitude
                            result["time_utc"] = message.get("time")
                            return result

        except OSError:
            pass

        return result


gps_service = GpsService()
