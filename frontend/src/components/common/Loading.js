import React from 'react';
import { Spinner, Container } from 'react-bootstrap';

const Loading = () => {
    return (
        <Container className="d-flex justify-content-center align-items-center" style={{ minHeight: '100vh' }}>
            <div className="text-center">
                <Spinner animation="border" role="status" variant="primary" style={{ width: '3rem', height: '3rem' }}>
                    <span className="visually-hidden">Loading...</span>
                </Spinner>
                <p className="mt-3">Loading...</p>
            </div>
        </Container>
    );
};

export default Loading;