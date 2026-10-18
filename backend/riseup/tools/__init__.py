"""External tool adapters (Adapter pattern) and the ToolManager that uses them."""

from riseup.tools.base import Tool, ToolResult
from riseup.tools.adapters import AlexaClient, FirebaseClient, TwilioClient
from riseup.tools.mock import MockAlexaClient, MockFirebaseClient, MockTwilioClient
from riseup.tools.manager import ToolManager, build_tool_manager

__all__ = [
    "AlexaClient",
    "FirebaseClient",
    "MockAlexaClient",
    "MockFirebaseClient",
    "MockTwilioClient",
    "Tool",
    "ToolManager",
    "ToolResult",
    "TwilioClient",
    "build_tool_manager",
]
