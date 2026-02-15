"""
HTTP server that receives commands from the Android app and passes them to mobile-use.
Run: uvicorn server:app --host 0.0.0.0 --port 8080
Then POST to http://<pi-ip>:8080/execute with JSON {"command": "..."}
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from executor import execute

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup/shutdown events."""
    logger.info("Yooni Pi server starting up on 0.0.0.0:8080")
    yield
    logger.info("Yooni Pi server shutting down")


app = FastAPI(
    title="Yooni Pi Server",
    description="Receives commands from Android app and passes them to mobile-use",
    version="1.0.0",
    lifespan=lifespan,
)


class CommandRequest(BaseModel):
    """Request model for command execution."""
    command: str = Field(..., min_length=1, description="Natural language command to execute")


class CommandResponse(BaseModel):
    """Response model for command execution."""
    success: bool
    message: str


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """Global exception handler that logs all unhandled exceptions with traces."""
    logger.exception("Unhandled exception while processing request to %s", request.url.path)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"error": "Internal server error"}
    )


@app.post("/execute", response_model=CommandResponse)
async def execute_command(request: CommandRequest):
    """
    Execute a natural language command via mobile-use.

    Args:
        request: CommandRequest containing the command to execute

    Returns:
        CommandResponse with success status and message

    Raises:
        HTTPException: 500 if command execution fails
    """
    command = request.command.strip()

    logger.info("Executing: %s", command[:80] + ("..." if len(command) > 80 else ""))

    try:
        success, message = execute(command)
        logger.info("Result: success=%s, message=%s", success, message[:100] if len(message) > 100 else message)

        if not success:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=message
            )

        return CommandResponse(success=True, message=message)

    except HTTPException:
        raise
    except Exception as e:
        logger.exception("Error executing command: %s", command[:80])
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Command execution failed: {str(e)}"
        )


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
