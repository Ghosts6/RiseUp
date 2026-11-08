FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY pyproject.toml ./
COPY riseup ./riseup
COPY riseup_cli ./riseup_cli
RUN pip install ".[postgres,redis]"

COPY alembic.ini app.py ./
COPY migrations ./migrations

RUN useradd --create-home riseup
USER riseup

EXPOSE 8000
# Tables come from Alembic in containers; create_all is only a local-dev convenience.
ENV AUTO_CREATE_TABLES=false
CMD ["sh", "-c", "alembic upgrade head && riseup-server"]
