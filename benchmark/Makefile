.PHONY: benchmark build up down clean

build:
	docker compose build

up:
	docker compose up -d pdf-ua-api weasyprint

down:
	docker compose down

benchmark:
	docker compose up -d --build pdf-ua-api weasyprint gotenberg
	docker compose build runner
	RUN_DATE=$$(date +%F) docker compose run --rm runner
	docker compose down

clean:
	docker compose down -v --remove-orphans
