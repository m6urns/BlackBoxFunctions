from flask import Flask, request, jsonify
import csv
import time
import json

app = Flask(__name__)

API_KEY = ''

@app.route('/log', methods=['POST'])
def log():
    auth_header = request.headers.get('Authorization')
    if not auth_header or auth_header.split()[1] != API_KEY:
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.json

    event_type = data.get('EventType', '')
    insert_text = data.get('InsertText', '')
    delete_text = data.get('DeleteText', '')
    source_location = data.get('SourceLocation', '')
    client_timestamp = data.get('ClientTimestamp', '')
    code_state_section = data.get('CodeStateSection', '')
    tool_instances = data.get('ToolInstances', '')
    edit_type = data.get('EditType', '')
    x_metadata = json.dumps(data.get('X-Metadata', {}))
    code_state_id = data.get('CodeStateID', '')
    x_compilable = data.get('X-Compilable', '')
    event_id = data.get('EventID', '')
    subject_id = data.get('SubjectID', '')
    assignment_id = data.get('AssignmentID', '')

    log_entry = [
        event_type,
        insert_text,
        delete_text,
        source_location,
        client_timestamp,
        code_state_section,
        tool_instances,
        edit_type,
        x_metadata,
        code_state_id,
        x_compilable,
        event_id,
        subject_id,
        assignment_id
    ]

    with open('/root/bbf-logging/bbf.log', mode='a', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(log_entry)

    return 'Logged', 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)