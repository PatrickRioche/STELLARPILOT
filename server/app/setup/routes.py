from app import _main_core as _core
from app.setup.service import setup_service


app = _core.app


@app.get("/setup/status")
def setup_status():
    indi_snapshot = _core.indi_service.status_snapshot()
    return setup_service.status(indi_snapshot)
