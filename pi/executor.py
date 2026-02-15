"""
Passes confirmed commands from the Android app to mobile-use.
Assumes mobile-use is installed and configured on the Pi.
"""

import logging
import os
import subprocess

logger = logging.getLogger(__name__)


def execute(command: str) -> tuple[bool, str]:
    """
    Run mobile-use with the given natural language command.

    Args:
        command: The confirmed action to execute (e.g. "Text Mom: I'll be there in 10 mins")

    Returns:
        (success, message) - success is True if mobile-use exited 0, else False
    """
    if not command or not command.strip():
        return False, "Empty command"

    try:
        cmd = ["python3", "-m", "minitap.mobile_use.main", command.strip()]
        cwd = os.getcwd()

        logger.info(f"Launching mobile-use:")
        logger.info(f"  Command: {' '.join(cmd)}")
        logger.info(f"  Working directory: {cwd}")

        # Run with output streaming to terminal (no capture)
        result = subprocess.run(
            cmd,
            timeout=300,  # 5 min max for long-running tasks
        )
        if result.returncode == 0:
            return True, "Command completed successfully"
        return False, f"Command failed with exit code {result.returncode}"
    except subprocess.TimeoutExpired:
        return False, "Task timed out"
    except FileNotFoundError:
        return False, "mobile-use not found (pip install mobile-use)"
    except Exception as e:
        return False, str(e)