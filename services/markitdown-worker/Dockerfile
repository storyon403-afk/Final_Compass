FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /worker

COPY services/markitdown-worker/pyproject.toml ./
COPY services/markitdown-worker/app ./app
RUN pip install --no-cache-dir '.[audio]'

USER nobody
EXPOSE 8090
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "1"]
