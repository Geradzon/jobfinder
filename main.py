import requests
from bs4 import BeautifulSoup
import smtplib
from email.mime.text import MIMEText
import os

EMAIL_ABSENDER = os.environ['EMAIL_ABSENDER']
EMAIL_PASSWORT = os.environ['EMAIL_PASSWORT']
EMAIL_ZIEL = os.environ['EMAIL_ZIEL']
STANDORT = 'viersen'
UMKREIS_KM = 40

SUCHBEGRIFFE = [
    'minijob', 'nebenjob', 'teilzeit', 'lager', 'helfer',
    'kommissionierer', 'zusteller', 'postbote', 'picnic', 'fahrer', 'reiniger', 'aushilfe'
]

def finde_jobs_arbeitsagentur():
    jobs = []
    for suchwort in SUCHBEGRIFFE:
        url = f'https://jobboerse.arbeitsagentur.de/jobsuche/suche?was={suchwort}&wo={STANDORT}&umkreis={UMKREIS_KM}'
        headers = {'User-Agent': 'Mozilla/5.0'}
        seite = requests.get(url, headers=headers)
        soup = BeautifulSoup(seite.text, 'html.parser')
        for eintrag in soup.select('.result-list__listing'):
            titel = eintrag.select_one('.result-list__job-title')
            link = eintrag.select_one('a')
            if titel and link:
                jobs.append(f"{titel.text.strip()} - https://jobboerse.arbeitsagentur.de{link.get('href')}")
    return jobs

def finde_jobs_kimeta():
    jobs = []
    for suchwort in SUCHBEGRIFFE:
        url = f'https://www.kimeta.de/stellenangebote?was={suchwort}&wo={STANDORT}&umkreis={UMKREIS_KM}'
        headers = {'User-Agent': 'Mozilla/5.0'}
        seite = requests.get(url, headers=headers)
        soup = BeautifulSoup(seite.text, 'html.parser')
        for eintrag in soup.select('.job'):
            titel = eintrag.select_one('.jobtitle')
            link = eintrag.select_one('a')
            if titel and link:
                jobs.append(f"{titel.text.strip()} - https://www.kimeta.de{link.get('href')}")
    return jobs

def finde_jobs_meinestadt():
    jobs = []
    for suchwort in SUCHBEGRIFFE:
        url = f'https://jobs.meinestadt.de/{STANDORT}/search?keywords={suchwort}&radius={UMKREIS_KM}'
        headers = {'User-Agent': 'Mozilla/5.0'}
        seite = requests.get(url, headers=headers)
        soup = BeautifulSoup(seite.text, 'html.parser')
        for eintrag in soup.select('.job-offer'):
            titel = eintrag.select_one('.job-offer__title')
            link = eintrag.select_one('a')
            if titel and link:
                jobs.append(f"{titel.text.strip()} - {link.get('href')}")
    return jobs

def sende_email(inhalt):
    msg = MIMEText('\n\n'.join(inhalt))
    msg['Subject'] = '📬 Neue Nebenjob-Angebote für dich'
    msg['From'] = EMAIL_ABSENDER
    msg['To'] = EMAIL_ZIEL

    with smtplib.SMTP_SSL('smtp.gmail.com', 465) as server:
        server.login(EMAIL_ABSENDER, EMAIL_PASSWORT)
        server.send_message(msg)

if __name__ == '__main__':
    jobs = finde_jobs_arbeitsagentur() + finde_jobs_kimeta() + finde_jobs_meinestadt()
    jobs = list(set(jobs))  # Duplikate entfernen

    if jobs:
        sende_email(jobs)
        print('✅ E-Mail mit Nebenjobs gesendet.')
    else:
        print('ℹ️ Keine neuen Jobs gefunden – keine Mail gesendet.')
