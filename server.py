from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse
from urllib.request import urlopen
from googlesearch import search
from urllib.parse import unquote
import socket
 

class testHTTPServer_RequestHandler(BaseHTTPRequestHandler):

	# GET
	def do_GET(self):
		query = unquote(urlparse(self.path).query)
		message = "Hello world!"
		if query != '':
			query_components = dict(qc.split("=") for qc in query.split("&"))
			question = query_components['q']
			opts = query_components['o'].split(',')
			message = collect(question, opts)

		# Send response status code
		self.send_response(200)

		# Send headers
		self.send_header('Content-type','text/html')
		self.end_headers()
		
		# Write content as utf-8 data
		self.wfile.write(bytes(message, "utf8"))
		return


def collect(question, opts):
	answers = {}
	for e in opts:
		answers[e] = 0
	n_of_sites = 3
	counter = 0
	for url in search(question, stop=2):
		counter += 1
		try:
			string = urlopen(url).read().decode('utf-8').lower()
			for e in opts:
				answers[e] += string.count(e)
			if n_of_sites == counter:
				break
		except:
			pass
		
	message = ""
	for e in opts:
		message += e + ':' + str(answers[e]) + '   '
	return message

def get_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # doesn't even have to be reachable
        s.connect(('10.255.255.255', 1))
        IP = s.getsockname()[0]
    except:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP

def run():
	try:
		#print('starting server...')
		server_address = (get_ip(), 8080)
		httpd = HTTPServer(server_address, testHTTPServer_RequestHandler)
		#print('running server...')
		httpd.serve_forever()

	except KeyboardInterrupt:
		#print('^C received, shutting down the web server')
		httpd.socket.close()
 
 
run()