"""
Passes confirmed commands from the Android app to mobile-use.
Assumes mobile-use is installed and configured on the Pi.
"""

import logging
import os
import subprocess
import time
from threading import Thread

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
        cwd = os.path.expanduser("~/Documents/mobile-use")
        venv_path = os.path.join(cwd, ".venv")

        # Build command that activates venv and runs mobile-use
        # Using bash -c to activate venv and run command in same shell
        bash_cmd = f'source "{venv_path}/bin/activate" && python -m minitap.mobile_use.main "{command.strip()}"'

        logger.info(f"Launching mobile-use:")
        logger.info(f"  Venv: {venv_path}")
        logger.info(f"  Command: {bash_cmd}")
        logger.info(f"  Working directory: {cwd}")

        # Set up environment with UTF-8 encoding to handle emojis
        env = os.environ.copy()
        env["PYTHONIOENCODING"] = "utf-8"

        # Start the process
        process = subprocess.Popen(
            ["bash", "-c", bash_cmd],
            cwd=cwd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1  # Line buffered
        )

        # Track output and last activity time
        output_lines = []
        last_activity = time.time()
        idle_timeout = 300  # 5 minutes of no output

        logger.info("mobile-use output:")

        # Read output line by line
        try:
            while True:
                line = process.stdout.readline()

                if line:
                    # We got output, update activity time
                    last_activity = time.time()
                    output_lines.append(line)

                    # Log the line
                    if line.strip():
                        logger.info(f"  {line.rstrip()}")

                # Check if process has finished
                if process.poll() is not None:
                    # Process finished, read any remaining output
                    remaining = process.stdout.read()
                    if remaining:
                        output_lines.append(remaining)
                        for line in remaining.splitlines():
                            if line.strip():
                                logger.info(f"  {line}")
                    break

                # Check for idle timeout (only if no line was read)
                if not line:
                    time.sleep(0.1)  # Small sleep to avoid busy waiting
                    if time.time() - last_activity > idle_timeout:
                        logger.warning("Process idle for 5 minutes, terminating...")
                        process.terminate()
                        try:
                            process.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait()
                        return False, "Task timed out due to inactivity (no output for 5 minutes)"

        finally:
            process.stdout.close()

        returncode = process.returncode

        if returncode == 0:
            return True, "Command completed successfully"
        return False, f"Command failed with exit code {returncode}"

    except FileNotFoundError:
        return False, "mobile-use not found (pip install mobile-use)"
    except Exception as e:
        logger.exception("Error executing command")
        return False, str(e)
