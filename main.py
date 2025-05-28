import requests
from bs4 import BeautifulSoup
import smtplib
from email.mime.text import MIMEText
import os

SUCHFELD = 'fachinformatiker ausbildung'
STANDORT = 'viersen'
UMKREIS_KM = 40

EMAIL_ABSENDER = os.environ['EMAIL_ABSENDER']
EMAIL_PASSWORT = os.environ['EMAIL_PASSWORT']
EMAIL_ZIEL = os.environ['EMAIL_ZIEL']  # Angepasst

def finde_jobs():
    url = f'https://jobboerse.arbeitsagentur.de/jobsuche/suche?was={SUCHFELD}&wo={STANDORT}&umkreis={UMKREIS_KM}'
    headers = {'User-Agent': 'Mozilla/5.0'}
    seite = requests.get(url, headers=headers)
    soup = BeautifulSoup(seite.text, 'html.parser')

    jobs = []
    for eintrag in soup.select('.result-list__listing'):
        titel = eintrag.select_one('.result-list__job-title')
        link = eintrag.select_one('a')
        if titel and link:
            jobs.append(f"{titel.text.strip()} - https://jobboerse.arbeitsagentur.de{link.get('href')}")

    return jobs

def sende_email(inhalt):
    msg = MIMEText('\n\n'.join(inhalt))
    msg['Subject'] = '🔍 Neue Jobangebote für dich'
    msg['From'] = EMAIL_ABSENDER
    msg['To'] = EMAIL_ZIEL

    with smtplib.SMTP_SSL('smtp.gmail.com', 465) as server:
        server.login(EMAIL_ABSENDER, EMAIL_PASSWORT)
        server.send_message(msg)

if __name__ == '__main__':
    jobs = finde_jobs()
    if jobs:
        sende_email(jobs)
        print('✅ E-Mail gesendet.')
    else:
        print('ℹ️ Keine neuen Jobs gefunden.')
